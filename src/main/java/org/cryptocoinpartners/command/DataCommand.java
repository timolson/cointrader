package org.cryptocoinpartners.command;

import org.cryptocoinpartners.report.DataSummaryReport;
import org.cryptocoinpartners.report.Report;


/**
 * @author Tim Olson
 */
public class DataCommand extends ReportCommand {


    public void printHelp() {
        out.println("data [summary]");
        out.println();
        out.println("\tPrints a count of Trade and Book entries for each Market");
    }


    protected Report getReport() {
        return injector.getInstance(DataSummaryReport.class);
    }

}
