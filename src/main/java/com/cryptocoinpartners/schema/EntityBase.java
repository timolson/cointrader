package com.cryptocoinpartners.schema;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.UUID;


/**
 * @author Tim Olson
 */
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@MappedSuperclass
@Table(indexes = { @Index(columnList = "id") })
public class EntityBase {


    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @Column( columnDefinition = "BINARY(16)", length = 16, updatable = false, nullable = false )
    public UUID getId() {
        return id;
    }


    protected EntityBase() {}


    protected void setId(UUID id) {
        this.id = id;
    }


    protected UUID id;
}
