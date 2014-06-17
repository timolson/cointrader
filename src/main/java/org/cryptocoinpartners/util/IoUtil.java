package org.cryptocoinpartners.util;

import au.com.bytecode.opencsv.CSVWriter;
import com.bethecoder.ascii_table.ASCIITable;
import org.cryptocoinpartners.report.TableOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;


/**
 * @author Tim Olson
 */
public class IoUtil {


    public static void outputAscii( TableOutput tableOutput )
    {
        if( tableOutput == null || tableOutput.rows == null || tableOutput.rows.length == 0 )
            System.out.println("no results");
        else
            ASCIITable.getInstance().printTable(tableOutput.headers, tableOutput.rows);
    }


    public static void outputCsv( TableOutput tableOutput, Writer out )
    {
        final CSVWriter writer = new CSVWriter(out);
        if( tableOutput == null )
            return;
        if( tableOutput.headers != null )
            writer.writeNext(tableOutput.headers);
        if( tableOutput.rows == null )
            return;
        for( String[] row : tableOutput.rows )
            writer.writeNext(row);
    }


    public static void writeCsv(TableOutput tableOutput, String filename) {
        FileWriter fw = null;
        try {
            fw = new FileWriter(filename);
            outputCsv(tableOutput, fw);
        }
        catch( IOException e ) {
            log.error("Could not write CSV file "+ filename, e);
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


    public static Logger log = LoggerFactory.getLogger(IoUtil.class);

}
