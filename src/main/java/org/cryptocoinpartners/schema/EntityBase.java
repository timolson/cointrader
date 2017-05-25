package org.cryptocoinpartners.schema;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.persistence.Version;

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
        log.debug(this.getClass().getSimpleName() + " : getUpdateLock - attempting to get update lock for id " + getId() + " called from class "
                + Thread.currentThread().getStackTrace()[2]);
        updateLock.acquire();
        log.debug(this.getClass().getSimpleName() + " : getUpdateLock - acquired  update lock for id " + getId() + " called from class "
                + Thread.currentThread().getStackTrace()[2]);

    }

    @Transient
    public void releaseUpdateLock() {
        // Log.debug(messages)
        log.debug(this.getClass().getSimpleName() + " : releaseUpdateLock - attempting to release update lock for id " + getId() + " called from class "
                + Thread.currentThread().getStackTrace()[2]);

        updateLock.release();
        log.debug(this.getClass().getSimpleName() + " : getUpdateLock - released update lock for id " + getId() + " called from class "
                + Thread.currentThread().getStackTrace()[2]);

    }

    @Transient
    public long getDelay(TimeUnit unit) {
        long diff = startTime - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    /*    @Override
        public int compareTo(Delayed o) {
            if (this.startTime < ((EntityBase) o).startTime) {
                return -1;
            }
            if (this.startTime > ((EntityBase) o).startTime) {
                return 1;
            }
            return 0;
        }*/

    @Override
    public int compareTo(EntityBase entityBase) {
        //if (this.equals(entityBase)) {
        int compareRevision = entityBase.getRevision();
        return (compareRevision - this.revision);
        //   }
        // return 1;
    }

    @Id
    @Column(columnDefinition = "BINARY(16)", length = 16, updatable = true, nullable = false)
    public UUID getId() {
        ensureId();
        return id;
    }

    @Version
    @Column(name = "version", columnDefinition = "integer DEFAULT 0", nullable = false)
    public long getVersion() {
        //  if (version == null)
        //    return 0;
        return version;
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
    }

    public synchronized void setRevision(int revision) {
        this.revision = revision;
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
        if (id == null || that.id == null)
            return false;
        return id.equals(that.id);

    }

    @Override
    public int hashCode() {
        // ensureId();
        //return Objects.hashCode(this.getId());

        return getId().toString().hashCode();
    }

    // JPA
    protected EntityBase() {
        startTime = System.currentTimeMillis();
        //  ensureId();

    }

    protected synchronized void setId(UUID id) {
        if (this.id == null)
            this.id = id;
    }

    public abstract void postPersist();

    public abstract void persit();

    public abstract EntityBase refresh();

    public long findRevisionById() {
        try {
            return getDao().findRevisionById(this.getClass(), this.getId());
        } catch (NullPointerException npe) {
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
        if (id == null)
            setId(UUID.randomUUID());
        // id = UUID.randomUUID();

        // if (startTime == 0)
        //   startTime = System.currentTimeMillis() + delay;
    }

    protected UUID id;
    protected long version;
    protected Integer retryCount;

    public abstract void prePersist();

}
