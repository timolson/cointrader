package org.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameter;
import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.Visitor;
import org.apache.commons.lang.ArrayUtils;

import java.util.*;


public abstract class JpaReportCommand extends ReportCommand
{


    protected Output runReport()
    {
        final JpaReportCommand.Query query = getQuery();
        final List<String[]> rowStrings = new ArrayList<String[]>();

        if( log.isTraceEnabled() )
            log.trace("Querying: "+query.queryStr+" / "+ArrayUtils.toString(query.params));
        if( limit != 0 ) {
            PersistUtil.queryEach(
                    new Visitor<Object[]>()
                    {
                        private int count = 0;
                        public boolean handleItem( Object[] row )
                        {
                            handleResult(row, rowStrings);
                            return ++count < limit;
                        }
                    },
                    limit,
                    query.queryStr,
                    query.params
            );
        }
        else {
            PersistUtil.queryEach(
                    new Visitor<Object[]>()
                    {
                        public boolean handleItem( Object[] row )
                        {
                            handleResult(row, rowStrings);
                            return true;
                        }
                    },
                    query.queryStr,
                    query.params
            );
        }
        String [][] rowStringTable = new String[rowStrings.size()][];
        rowStrings.toArray(rowStringTable);
        final String[] headers = query.headers;
        return new Output(headers, rowStringTable);
    }


    protected void handleResult( Object[] row, List<String[]> rowStrings )
    {
        final String[] rowFormat = formatRow(row);
        rowStrings.add(rowFormat);
    }


    @Parameter(names={"-l","-limit"})
    protected int limit = 0;


    protected class Query
    {
        public Query( String[] headers, String queryStr, Object[] params )
        {
            this.headers = headers;
            this.queryStr = queryStr;
            this.params = params;
        }


        public Query( String[] headers, String queryStr )
        {
            this.headers = headers;
            this.queryStr = queryStr;
            this.params = new Object[] { };
        }


        String[] headers;
        String queryStr;
        Object[] params;
    }


    protected abstract Query getQuery();


    protected String[] formatRow(Object[] row) {
        final String[] result = new String[row.length];
        for( int i = 0; i < row.length; i++ ) {
            Object item = row[i];
            result[i] = formatColumn(i,item);
        }
        return result;
    }


    protected String formatColumn( int columnIndex, Object item )
    {
        return String.valueOf(item);
    }


}
