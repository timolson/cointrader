package org.cryptocoinpartners.schema.dao;

import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.util.Visitor;
import org.joda.time.Interval;

public interface BookDao extends Dao {
    public void find(Interval timeInterval, Visitor<Book> visitor);

    public void findAll(Visitor<Book> visitor);

}
