package org.cryptocoinpartners.schema;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.persistence.Basic;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import org.hibernate.annotations.Type;
import org.joda.time.Instant;

/**
 * @author Tim Olson
 */
@MappedSuperclass
//@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class Temporal extends EntityBase {

	public Temporal(Instant time) {
		super();
		this.id = getId();
		this.time = time;
		this.dateTime = time.toDate();
		this.timestamp = time.getMillis();
		this.updateTime = new AtomicReference<Instant>(time);
		this.updateTimestamp = new AtomicLong(time.getMillis());
	}

	/** For Events, this is the time the Event itself occured, not the time we received the Event.  It should be remote
	 * server time if available, and local time if the object was created locally */
	@Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
	@Basic(optional = false)
	public Instant getTime() {
		return time;
	}

	@Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
	@Basic(optional = true)
	public Instant getUpdateTime() {
		if (updateTime == null)
			return getTime();
		else
			return updateTime.get();
	}

	@Transient
	public Date getDateTime() {
		return dateTime;
	}

	//  @Transient
	// @AttributeOverride(name = "timestamp", column = @Column(name = "version")) we need ot set this to last update time.
	public long getTimestamp() {
		if (getTime() == null)
			return 0L;
		else
			return getTime().getMillis();
		// return timestamp;
	}

	@Basic(optional = true)
	public long getUpdateTimestamp() {
		if (getUpdateTime() == null)
			return getTimestamp();
		else
			return getUpdateTime().getMillis();
		// return timestamp;
	}

	//@Override
	//@AttributeOverride(name = "version", column = @Column(name = "version"))
	// public long getVersion() {
	//if (timestamp == null)
	//  return 0;
	//   version = timestamp;
	// return version;
	// }

	// JPA
	protected Temporal() {
	}

	protected synchronized void setTimestamp(long timestamp) {
		//  this.time = time;
		//this.dateTime = time.toDate();
		this.timestamp = timestamp;
	}

	protected synchronized void setTime(Instant time) {
		this.time = time;
		this.dateTime = time.toDate();
		setTimestamp(time.getMillis());
	}

	protected synchronized void setUpdateTimestamp(Long timestamp) {
		//  this.time = time;
		//this.dateTime = time.toDate();
		if (timestamp != null)
			this.updateTimestamp = new AtomicLong(timestamp);
	}

	protected synchronized void setUpdateTime(Instant time) {
		this.updateTime = new AtomicReference<Instant>(time);
		if (time != null)
			setUpdateTimestamp(time.getMillis());
	}

	protected Instant time;
	private long timestamp;
	private Date dateTime;
	protected AtomicReference<Instant> updateTime;
	private AtomicLong updateTimestamp;

}
