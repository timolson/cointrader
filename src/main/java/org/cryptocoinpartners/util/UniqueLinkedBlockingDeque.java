package org.cryptocoinpartners.util;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * Linked blocking dequeue with {@link #add(Object)} method, which adds only element, that is not already in the queue.
 */
public class UniqueLinkedBlockingDeque<T> extends LinkedBlockingDeque<T> {

    @Override
    public void put(T t) throws InterruptedException {

        if (!super.contains(t)) {
            try {
                super.put(t);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                throw e;
            }
        }

    }

}
