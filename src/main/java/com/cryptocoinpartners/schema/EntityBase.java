package com.cryptocoinpartners.schema;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;


/**
 * @author Tim Olson
 */
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@MappedSuperclass
public class EntityBase {

    /**
     * We use a local database ID by default.  Large tables aggregated from multiple sources also have a getGuid()
     */
    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    public long getId() { return id; }


    protected void setId(long id) { this.id = id; }


    protected EntityBase() {}


    @SuppressWarnings("FieldCanBeLocal")
    private long id = -1;
}
