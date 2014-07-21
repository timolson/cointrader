package org.cryptocoinpartners.util;

import au.com.bytecode.opencsv.CSVWriter;
import com.bethecoder.ascii_table.ASCIITable;
import com.clutch.dates.StringToTime;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.SaveTicksCsv;
import org.cryptocoinpartners.module.TickWindow;
import org.cryptocoinpartners.report.TableOutput;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;


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


    public static void dumpTicks(String filename, String startString, String endString, boolean allowNa) {
        // parse the start and end times
        Date start = null;
        if( startString != null ) {
            try {
                start = new StringToTime(startString);
            }
            catch( Exception e ) {
                log.error("Could not parse start time \"" + startString + "\"");
                System.exit(7001);
            }
        }

        Date end = null;
        if( endString != null ) {
            try {
                end = new StringToTime(endString);
            }
            catch( Exception e ) {
                log.error("Could not parse end time \"" + endString + "\"");
                System.exit(7001);
            }
        }


        Replay replay;
        if( start == null ) {
            if( end == null )
                replay = Replay.all(false);
            else
                replay = Replay.until(new Instant(end),false);
        }
        else if( end == null )
            replay = Replay.since(new Instant(start),false);
        else
            replay = Replay.between(new Instant(start), new Instant(end),false);


        Context context = replay.getContext();
        context.attach(TickWindow.class); // generate ticks
        context.attach(SaveTicksCsv.class,  // save ticks as csv
                       ConfigUtil.forModule("savetickscsv.filename", filename,
                                            "savetickscsv.na", allowNa)
                      );
        replay.run();
        context.destroy();
    }


    public static Logger log = LoggerFactory.getLogger(IoUtil.class);

}
