package org.cryptocoinpartners.bin.command;

import au.com.bytecode.opencsv.CSVWriter;
import com.bethecoder.ascii_table.ASCIITable;
import com.beust.jcommander.Parameter;

import javax.annotation.Nullable;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;


public abstract class ReportCommand extends Command
{
    public void run()
    {
        Output output = runReport();
        output(output);
    }


    protected abstract Output runReport();


    protected void output( Output output )
    {
        if( csv != null ) {
            FileWriter fw = null;
            try {
                fw = new FileWriter(csv);
                outputCsv(output, fw);
            }
            catch( IOException e ) {
                log.error("Could not write CSV file "+csv, e);
            }
            finally {
                if( fw != null ) {
                    try {
                        fw.close();
                    }
                    catch( IOException e ) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }
        else
            outputAscii(output);
    }


    protected void outputAscii( Output output )
    {
        if( output.rows.length == 0 )
            System.out.println("no results");
        else
            ASCIITable.getInstance().printTable(output.headers, output.rows);
    }


    protected void outputCsv( Output output, Writer out )
    {
        final CSVWriter writer = new CSVWriter(out);
        if( output.headers != null )
            writer.writeNext(output.headers);
        for( String[] row : output.rows )
            writer.writeNext(row);
    }


    protected class Output {
        @Nullable String[] headers;
        String[][] rows;


        public Output( @Nullable String[] headers, String[][] rows ) { this.headers = headers; this.rows = rows; }
    }



    @Parameter(names = "-csv", description = "specifies a file for output in CSV format")
    private String csv = null;
}
