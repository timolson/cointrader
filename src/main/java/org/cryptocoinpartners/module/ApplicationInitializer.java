package org.cryptocoinpartners.module;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import javax.inject.Singleton;
import javax.persistence.ElementCollection;

import org.cryptocoinpartners.schema.EntityBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import com.google.inject.persist.PersistService;

@Singleton
public class ApplicationInitializer implements Context.AttachListener {
    private Map<String, String> config;
    private static ListeningExecutorService insertPool = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.applicationInitalizer");

    private static ExecutorService mergeService = Executors.newFixedThreadPool(1);
    private static ExecutorService deleteService = Executors.newFixedThreadPool(1);

    private static ExecutorService insertService = Executors.newFixedThreadPool(1);
    //private static BlockingQueue insertQueue = new DelayQueue();
    //private static BlockingQueue mergeQueue = new DelayQueue();
    private static LinkedBlockingDeque insertQueue = new LinkedBlockingDeque();
    private static LinkedBlockingDeque mergeQueue = new LinkedBlockingDeque();
    private static BlockingQueue deleteQueue = new DelayQueue();

    //   LinkedBlockingQueue<EntityBase[]>();

    @Inject
    ApplicationInitializer(PersistService service) {
        service.start();
        ListenableFuture<String> insertFuture = insertPool.submit(new persistRunnable(insertQueue));

        // Future<Void> insertFuture = insertService.submit(new persistRunnable(insertQueue));
        Future<Void> mergeFuture = mergeService.submit(new mergeRunnable(mergeQueue));
        Future<Void> deleteFuture = deleteService.submit(new deleteRunnable(deleteQueue));

        insertFuture.addListener(new insertMointorRunnable(insertFuture), MoreExecutors.sameThreadExecutor());

        //    try {
        //      insertFuture.get();
        //} catch (InterruptedException e) {
        //  Thread.currentThread().interrupt();
        // } catch (ExecutionException e) {

        // System.out.println("** RuntimeException from thread ");
        //   e.getCause().printStackTrace(System.out);
        // }

        // insertService.submit(new persistRunnable(insertQueue));
        // mergeService.submit(new mergeRunnable(mergeQueue));

        //    DaoJpa.mergeRunnable
        // At this point JPA is started and ready.

        // other application initializations if necessary
    }

    protected void setInsertQueue(LinkedBlockingDeque insertQueue) {
        this.insertQueue = insertQueue;
    }

    protected void setMergeQueue(LinkedBlockingDeque mergeQueue) {
        this.mergeQueue = mergeQueue;
    }

    protected void setDeleteQueue(LinkedBlockingDeque deleteQueue) {
        this.deleteQueue = deleteQueue;
    }

    public LinkedBlockingDeque getMergeQueue() {
        return mergeQueue;
    }

    public LinkedBlockingDeque getInsertQueue() {
        return insertQueue;
    }

    public BlockingQueue getDeleteQueue() {
        return deleteQueue;
    }

    public class insertMointorRunnable implements Runnable {
        private ListenableFuture<String> insertFuture;

        public insertMointorRunnable(ListenableFuture<String> insertFuture) {
            this.insertFuture = insertFuture;

        }

        @Override
        public void run() {
            while (true)
                try {
                    insertFuture.get();
                    //...process web site contents
                } catch (InterruptedException e) {
                    log.error(" " + this.getClass().getSimpleName() + ":run, full stack trace follows:", e);
                } catch (ExecutionException e) {
                    insertFuture = insertPool.submit(new persistRunnable(insertQueue));
                    log.error(" " + this.getClass().getSimpleName() + ":run, full stack trace follows:", e);
                }
        }
    }

    public class persistRunnable implements Callable {

        private final LinkedBlockingDeque peristQueue;

        @Override
        public Void call() {
            while (true) {
                try {
                    EntityBase entity = (EntityBase) peristQueue.take();
                    if (entity.getPeristanceAction() != null) {
                        switch (entity.getPeristanceAction()) {
                        //  entity.getDao().persistEntities(entity);
                            case NEW:
                                entity.getDao().persistEntities(entity);
                                break;
                            case MERGE:
                                entity.getDao().mergeEntities(entity);
                                break;
                            case DELETE:
                                entity.getDao().deleteEntities(entity);
                                break;
                            default:
                                entity.getDao().persistEntities(entity);
                                break;
                        }
                    } else
                        entity.getDao().persistEntities(entity);
                    //  EntityBase[] entities = peristQueue.take();
                    // for (EntityBase entity : entities)

                } catch (Exception | Error e) {

                    log.error(" " + this.getClass().getSimpleName() + ":call, full stack trace follows:", e);
                    //   Thread.currentThread().interrupt();
                    // return null; // supposing there is no cleanup or other stuff to be done

                }
            }

        }

        public persistRunnable(LinkedBlockingDeque peristQueue) {
            this.peristQueue = peristQueue;

        }

    }

    public class mergeRunnable implements Callable {

        private final LinkedBlockingDeque mergeQueue;

        @Override
        public Void call() {
            while (true) {
                try {
                    EntityBase entity = (EntityBase) mergeQueue.take();
                    if (entity.getPeristanceAction() != null) {
                        switch (entity.getPeristanceAction()) {
                            case NEW:
                                entity.getDao().persistEntities(entity);
                                break;
                            case MERGE:
                                entity.getDao().mergeEntities(entity);
                                break;
                            case DELETE:
                                entity.getDao().deleteEntities(entity);
                                break;
                            default:
                                entity.getDao().mergeEntities(entity);
                                break;
                        }
                    } else
                        entity.getDao().mergeEntities(entity);
                } catch (Exception | Error e) {
                    log.error(" " + this.getClass().getSimpleName() + ":call, full stack trace follows:", e);

                }
            }

        }

        public mergeRunnable(LinkedBlockingDeque mergeQueue) {
            this.mergeQueue = mergeQueue;

        }

    }

    public class deleteRunnable implements Callable {

        private final BlockingQueue deleteQueue;

        @Override
        public Void call() {
            while (true) {
                try {

                    EntityBase entity = (EntityBase) deleteQueue.take();
                    if (entity.getPeristanceAction() != null) {
                        switch (entity.getPeristanceAction()) {
                            case NEW:
                                entity.getDao().persistEntities(entity);
                                break;
                            case MERGE:
                                entity.getDao().mergeEntities(entity);
                                break;
                            case DELETE:
                                entity.getDao().deleteEntities(entity);
                                break;
                            default:
                                entity.getDao().deleteEntities(entity);
                                break;
                        }
                    } else
                        entity.getDao().deleteEntities(entity);
                    // for (EntityBase entity : entities)

                    // dao.mergeEntities(entities);
                } catch (Exception | Error e) {
                    log.error(" " + this.getClass().getSimpleName() + ":call, full stack trace follows:", e);

                }
            }

        }

        public deleteRunnable(BlockingQueue deleteQueue) {
            this.deleteQueue = deleteQueue;

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
