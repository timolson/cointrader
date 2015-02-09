package org.cryptocoinpartners.report;

/**
 * @author Tim Olson
 */
public class DataSummaryReport extends JpaReport {

    @Override
    protected Query getQuery() {
        return new Query(
                    new String[]{"Market", "Num Trades", "Num Books"},
                    "select m, count(*) as num, (select count(*) from Book b where b.market=m) "+
                    "from Trade t, Market m " +
                    "where t.market=m group by m order by num desc"
                );
    }

}
