package org.cryptocoinpartners.schema;

import javax.persistence.Entity;

/**
 * An Authorization represents an approval or signing of a secured action like a reconciliation Adjustment.
 *
 * @author Tim Olson
 */
@Entity
public class Authorization extends EntityBase {

    public Authorization(String notes) {
        this.notes = notes;
    }

    public String getNotes() {
        return notes;
    }

    // todo add a signature or something

    // JPA
    protected Authorization() {
    }

    protected void setNotes(String notes) {
        this.notes = notes;
    }

    private String notes;

    @Override
    public void persit() {
        // TODO Auto-generated method stub

    }

    @Override
    public void detach() {
        // TODO Auto-generated method stub

    }

    @Override
    public void merge() {
        // TODO Auto-generated method stub

    }

}
