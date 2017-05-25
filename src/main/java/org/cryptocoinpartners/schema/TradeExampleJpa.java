package org.cryptocoinpartners.schema;

import javax.persistence.EntityManager;

import com.google.inject.Inject;

public class TradeExampleJpa {

    protected EntityManager entityManager;

    @Inject
    public TradeExampleJpa(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void persistInNewTransaction(EntityBase... entities) {
        entityManager.getTransaction().begin();
        for (EntityBase entity : entities)
            persist(entity);
        entityManager.getTransaction().commit();
    }

    public void persist(EntityBase entity) {
        entityManager.persist(entity);
    }

}
