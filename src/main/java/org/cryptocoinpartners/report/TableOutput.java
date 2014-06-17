package org.cryptocoinpartners.report;

import javax.annotation.Nullable;


/**
* @author Tim Olson
*/
public class TableOutput {

    @Nullable
    public final String[] headers;

    public final String[][] rows;

    public TableOutput(@Nullable String[] headers, String[][] rows) { this.headers = headers; this.rows = rows; }

}
