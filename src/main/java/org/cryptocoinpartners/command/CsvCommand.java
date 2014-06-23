package org.cryptocoinpartners.command;

import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.module.SaveTicksCsv;
import org.cryptocoinpartners.util.IoUtil;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class CsvCommand extends AntlrCommandBase {


    public String getUsageHelp() {
        return "csv '{filename}' [from {start_date}] [(to|til) {end_date}] [by {tick_duration}]";
    }


    public String getExtraHelp() {
        return "Writes a csv file with columns "+ StringUtils.join(SaveTicksCsv.headers,", ") + ".  If start_date or end_date are specified, the data set is limited, otherwise everything in the database is output.  tick_duration is currently ignored and only 1-minute tick invervals are written.";
    }


    public void run() {
        out.println("Dumping ticks...");
        IoUtil.dumpTicks(filename,startDate,endDate,false);
        out.println("Wrote file "+filename);
    }


    String filename;
    String startDate;
    String endDate;
    String tickDuration;

}
