package org.cryptocoinpartners.schema;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.TableGenerator;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.util.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
// @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@MappedSuperclass
public abstract class EntityBase implements java.io.Serializable, Cloneable, Comparable<EntityBase> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7893439827939854533L;
	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.entityBase");

	static long delay;
	private long startTime;
	private int attempt;
	private int revision;
	private PersistanceAction persistanceAction;
	//Only 1 thread at a time can upda
	private final transient Semaphore updateLock = new Semaphore(1);

	@Transient
	public long getDelay() {
		if (delay == 0)
			return ConfigUtil.combined().getInt("db.writer.delay");
		return delay;
	}

	@Override
	public EntityBase clone() throws CloneNotSupportedException {
		return (EntityBase) super.clone();
	}

	@Transient
	public void getUpdateLock() throws InterruptedException {
		log.debug(this.getClass().getSimpleName() + " : getUpdateLock - attempting to get update lock for id " + getUuid() + " called from class "
				+ Thread.currentThread().getStackTrace()[2]);
		updateLock.acquire();
		log.debug(this.getClass().getSimpleName() + " : getUpdateLock - acquired  update lock for id " + getUuid() + " called from class "
				+ Thread.currentThread().getStackTrace()[2]);

	}

	@Transient
	public void releaseUpdateLock() {
		// Log.debug(messages)
		log.debug(this.getClass().getSimpleName() + " : releaseUpdateLock - attempting to release update lock for id " + getUuid() + " called from class "
				+ Thread.currentThread().getStackTrace()[2]);

		updateLock.release();
		log.debug(this.getClass().getSimpleName() + " : getUpdateLock - released update lock for id " + getUuid() + " called from class "
				+ Thread.currentThread().getStackTrace()[2]);

	}

	@Transient
	public long getDelay(TimeUnit unit) {
		long diff = startTime - System.currentTimeMillis();
		return unit.convert(diff, TimeUnit.MILLISECONDS);
	}

	/*
	 * @Override public int compareTo(Delayed o) { if (this.startTime < ((EntityBase) o).startTime) { return -1; } if (this.startTime > ((EntityBase)
	 * o).startTime) { return 1; } return 0; }
	 */

	@Override
	public int compareTo(EntityBase entityBase) {
		//if (this.equals(entityBase)) {
		int compareRevision = entityBase.getRevision();
		return (compareRevision - this.revision);
		//   }
		// return 1;
	}

	@Transient
	public boolean isPersisted() {

		return persisted;
	}

	@Column(columnDefinition = "BINARY(16)", length = 16, updatable = true, nullable = false)
	public UUID getUuid() {
		ensureId();
		return uuid;
	}

	//	@Id
	//	@GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
	//	@GenericGenerator(name = "native", strategy = "native")

	//@GeneratedValue(strategy = GenerationType.AUTO)
	//	@Column(name = "id", updatable = false, nullable = false)
	//@GeneratedValue(strategy = GenerationType.TABLE)
	//@Id
	//@GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
	//@GenericGenerator(name = "native", strategy = "native")
	//@Column(name = "id", updatable = false, nullable = false) 
	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "ConfirmationCodeGenerator")
	@TableGenerator(table = "SEQUENCES", name = "ConfirmationCodeGenerator")
	public Long getId() {
		return id;
	}

	//@Version
	@Column(name = "version", columnDefinition = "integer DEFAULT 0", nullable = false)
	public long getVersion() {
		//  if (version == null)
		//    return 0;
		return version;
	}

	@Transient
	public boolean getPersisted() {
		//  if (version == null)
		//    return 0;
		if (originalEntity != null)
			return originalEntity.persisted;
		return persisted;
	}

	@Transient
	public EntityBase getOriginalEntity() {
		//  if (version == null)
		//    return 0;
		return originalEntity;
	}

	@Column(name = "revision", columnDefinition = "integer DEFAULT 0", nullable = false)
	public int getRevision() {
		//  if (version == null)
		//    return 0;
		return revision;
	}

	@Transient
	public int getAttempt() {
		//  if (version == null)
		//    return 0;
		return attempt;
	}

	public synchronized void setAttempt(int attempt) {
		this.attempt = attempt;
	}

	@Nullable
	public PersistanceAction getPeristanceAction() {
		//  if (version == null)
		//    return 0;
		return persistanceAction;
	}

	public synchronized void setPeristanceAction(PersistanceAction persistanceAction) {
		this.persistanceAction = persistanceAction;
	}

	@Transient
	public synchronized void setStartTime(long delay) {
		this.delay = delay;
		this.startTime = System.currentTimeMillis() + delay;

	}

	public synchronized void setVersion(long version) {

		this.version = version;
		if (this.originalEntity != null)
			this.originalEntity.version = version;
	}

	public synchronized void setPersisted(Boolean persisted) {
		if (persisted) {
			this.setPeristanceAction(PersistanceAction.MERGE);
			if (this.originalEntity != null)
				this.originalEntity.setPeristanceAction(PersistanceAction.MERGE);
		}
		if (this.originalEntity != null)
			this.originalEntity.persisted = persisted;

		this.persisted = persisted;
	}

	public synchronized void setRevision(int revision) {
		this.revision = revision;
		if (this.originalEntity != null)
			this.originalEntity.revision = revision;
	}

	public synchronized void setOriginalEntity(EntityBase originalEntity) {
		this.originalEntity = originalEntity;
	}

	@Override
	public String toString() {
		return "DelayedRunnable [delayMS=" + delay + ",(ms)=" + getDelay(TimeUnit.MILLISECONDS) + "]";
	}

	@Transient
	public Integer getRetryCount() {
		if (retryCount == null)
			return 0;
		return retryCount;
	}

	public synchronized void setRetryCount(Integer retryCount) {
		this.retryCount = retryCount;
	}

	public void incermentRetryCount() {
		retryCount = getRetryCount() + 1;
	}

	@Override
	public boolean equals(Object o) {
		// generated by IDEA
		if (this == o)
			return true;
		if (!(o instanceof EntityBase))
			return false;
		EntityBase that = (EntityBase) o;
		// Need to check these are not null as assinged when persisted so might not yet be present when objects are compared
		if (uuid == null || that.uuid == null)
			return false;
		return uuid.equals(that.uuid);

	}

	@Override
	public int hashCode() {
		// ensureId();
		//return Objects.hashCode(this.getId());

		return getUuid().toString().hashCode();
	}

	// JPA
	protected EntityBase() {
		startTime = System.currentTimeMillis();
		//  ensureId();

	}

	protected synchronized void setUuid(UUID uuid) {

		this.uuid = uuid;
	}

	public synchronized void setId(Long id) {
		this.id = id;
	}

	public abstract void postPersist();

	public abstract void persit();

	public abstract void persitParents();

	@Transient
	public abstract EntityBase getParent();

	public abstract EntityBase refresh();

	protected void getParents(EntityBase child, Collection<EntityBase> parents) {
		if (child.getParent() == null) {
			parents.add(child);
			return;
		}

		getParents(child.getParent(), parents);
		parents.add(child);

	}

	public long findRevisionById() {
		try {
			long intRev = getDao().findRevisionById(this.getClass(), this.getId());
			this.setPersisted(true);
			return intRev;
		} catch (NullPointerException npe) {
			this.setPersisted(false);
			return 0;
		}
	}

	public long findVersionById() {
		try {
			return getDao().findVersionById(this.getClass(), this.getId());
		} catch (NullPointerException npe) {
			return 0;
		}
	}

	public abstract void delete();

	@Transient
	public abstract Dao getDao();

	@Transient
	public abstract void setDao(Dao dao);

	public abstract void detach();

	public abstract void merge();

	private void ensureId() {
		if (uuid == null)
			setUuid(UUID.randomUUID());
		// id = UUID.randomUUID();

		// if (startTime == 0)
		//   startTime = System.currentTimeMillis() + delay;
	}

	protected UUID uuid;

	protected Long id;
	protected long version;
	protected boolean persisted = false;
	protected EntityBase originalEntity;
	protected Integer retryCount;

	public abstract void prePersist();

}
