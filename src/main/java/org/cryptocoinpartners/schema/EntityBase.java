package org.cryptocoinpartners.schema;

import javax.persistence.*;
import java.util.UUID;


/**
 * @author Tim Olson
 */
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@MappedSuperclass
public abstract class EntityBase {


    @Id
    @Column( columnDefinition = "BINARY(16)", length = 16, updatable = false, nullable = false )
    public UUID getId() { return id; }


    // JPA
    protected EntityBase() {}
    protected void setId(UUID id) { this.id = id; }


    @PrePersist
    protected void ensureId() {
        if( id == null )
            id = UUID.randomUUID();
    }


    private UUID id;
}
