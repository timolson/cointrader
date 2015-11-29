package org.cryptocoinpartners.schema;

import java.io.Serializable;
import java.util.UUID;

import javax.persistence.Embeddable;

@Embeddable
public class OrderUpdateId implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -4285117148396302106L;
    private UUID id;
    private Long sequence;

    public OrderUpdateId() {
    }

    public OrderUpdateId(UUID id, Long sequence) {
        this.id = id;
        this.sequence = sequence;
    }

    public Long getSequence() {
        return sequence;
    }

    public UUID getId() {
        ensureId();
        return id;
    }

    private void ensureId() {
        if (id == null)
            id = UUID.randomUUID();

    }

    public void setSequence(Long sequence) {

        this.sequence = sequence;
    }

    public void setID(UUID id) {

        this.id = id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((sequence == null) ? 0 : sequence.hashCode());
        result = prime * result + id.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OrderUpdateId other = (OrderUpdateId) obj;
        if (sequence == null) {
            if (other.sequence != null)
                return false;
        } else if (!sequence.equals(other.sequence))
            return false;
        if (id != other.id)
            return false;
        return true;
    }

}
