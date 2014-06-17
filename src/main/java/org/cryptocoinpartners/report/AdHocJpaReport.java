package org.cryptocoinpartners.report;

/**
 * @author Tim Olson
 */
public class AdHocJpaReport extends JpaReport {


    public void setQueryString(String query) {
        this.query = query;
    }


    protected Query getQuery()
    {
        return new Query(null,query);
    }


    private String query;
}
