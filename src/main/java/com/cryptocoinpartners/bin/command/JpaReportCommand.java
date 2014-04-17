package com.cryptocoinpartners.bin.command;

import com.cryptocoinpartners.util.PersistUtil;

import java.util.ArrayList;
import java.util.List;


public abstract class JpaReportCommand extends ReportCommand
{


    protected Output runReport()
    {
        final JpaReportCommand.Query query = getQuery();
        final List<String[]> rowStrings = new ArrayList<String[]>();

        PersistUtil.queryEach(
                Object[].class,
                new PersistUtil.RowHandler<Object[]>()
                {
                    public boolean handleEntity( Object[] row )
                    {
                        final String[] rowFormat = formatRow(row);
                        rowStrings.add(rowFormat);
                        return true;
                    }
                },
                query.queryStr,
                query.params
        );
        String [][] rowStringTable = new String[rowStrings.size()][];
        rowStrings.toArray(rowStringTable);
        final String[] headers = query.headers;
        return new Output(headers, rowStringTable);
    }


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
        return item.toString();
    }


}
