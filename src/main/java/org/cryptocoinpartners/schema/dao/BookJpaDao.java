package org.cryptocoinpartners.schema.dao;

import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.util.Visitor;
import org.joda.time.Interval;

public class BookJpaDao extends DaoJpa implements BookDao {

    /**
     * 
     */
    private static final long serialVersionUID = 8420958344070346740L;

    @Override
    public void find(Interval timeInterval, Visitor<Book> visitor) {
        queryEach(Book.class, visitor, "select b from Book b where time > ?1 and time < ?2", timeInterval.getStartMillis(), timeInterval.getEndMillis());

    }

    @Override
    public void findAll(Visitor<Book> visitor) {
        queryEach(Book.class, visitor, "select b from Book b");

    }

    /*    @Override
        @Transactional
        public void persist(EntityBase... entities) {
            ///  unitOfWork.begin();
            try {
                for (EntityBase entity : entities)

                    // EntityBase existingEntity = entityManager.find(entity.getClass(), entity.getId());
                    //if (existingEntity != null) {
                    //entityManager.merge(entity);
                    //entityManager.flush();
                    ///    } else

                    //  entityManager.get().getTransaction().begin();

                    entityManager.get().persist(entity);
                //entityManager.flush();

                // entityManager.get().getTransaction().commit();
                //  // log.debug("persisting entity " + entity.getClass().getSimpleName() + " " + entity);
                //   entityManager.getTransaction().commit();
            } catch (OptimisticLockException ole) {
                log.error("Unable to merge record" + ole);
            }

            catch (Exception | Error ex) {

                log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);
                ex.printStackTrace();

            } finally {
                // unitOfWork.end();
            }

        }*/

}
