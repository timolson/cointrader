package org.cryptocoinpartners.util;

public interface Visitor<T> {
    /**
     @param item the next item in the collection being traversed
     @return true to continue with the next result item, or false to halt iteration
     */
    boolean handleItem( T item );
}
