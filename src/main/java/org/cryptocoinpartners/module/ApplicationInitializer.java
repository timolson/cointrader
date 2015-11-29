package org.cryptocoinpartners.module;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.persistence.ElementCollection;

import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.dao.BookDao;
import org.cryptocoinpartners.schema.dao.DaoJpa;

import com.google.inject.Inject;
import com.google.inject.persist.PersistService;

public class ApplicationInitializer implements Context.AttachListener {
    private Map<String, String> config;
    private static ExecutorService mergeService = Executors.newFixedThreadPool(5);
    private static ExecutorService insertService = Executors.newFixedThreadPool(5);

    @Inject
    ApplicationInitializer(PersistService service, BookDao dao) {
        service.start();
        //mergeService.submit(dao.);
        //insertService.submit(new persistRunnable(dao));

        //    DaoJpa.mergeRunnable
        // At this point JPA is started and ready.

        // other application initializations if necessary
    }

    public class persistRunnable implements Runnable {

        private final BookDao dao;

        @Override
        public void run() {
            while (true) {
                try {
                    EntityBase[] entities = dao.getInsertQueue().take();
                    dao.persistEntities(entities);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // supposing there is no cleanup or other stuff to be done

                }
            }

        }

        public persistRunnable(BookDao dao) {
            this.dao = dao;

        }

    }

    public class mergeRunnable implements Runnable {

        private final DaoJpa dao;

        @Override
        public void run() {
            while (true) {
                try {
                    EntityBase[] entities = dao.getMergeQueue().take();
                    dao.mergeEntities(entities);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // supposing there is no cleanup or other stuff to be done

                }
            }

        }

        public mergeRunnable(DaoJpa dao) {
            this.dao = dao;

        }

    }

    @ElementCollection
    public Map<String, String> getConfig() {
        return config;
    }

    protected void setConfig(Map<String, String> config) {
        this.config = config;
    }

    @Override
    public void afterAttach(final Context context) {
        // attach the actual Strategy instead of this StrategyInstance
        // Set ourselves as the StrategyInstance
        //      context.loadStatements("BasicPortfolioService");
        //   context.attach("BaseOrderService", new MapConfiguration(config), new Module()

        // {
        //   @Override
        // public void configure(Binder binder) {

        //     binder.install(new FactoryModuleBuilder().build(GeneralOrderFactory.class));
        //   binder.install(new FactoryModuleBuilder().build(SpecificOrderFactory.class));

        //  }
        //});

    }
}
