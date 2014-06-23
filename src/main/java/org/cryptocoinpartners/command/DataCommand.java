package org.cryptocoinpartners.command;

import org.cryptocoinpartners.report.DataSummaryReport;
import org.cryptocoinpartners.report.Report;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class DataCommand extends ReportCommand {


    public String getUsageHelp() {
        return "data [summary]";
    }


    public String getExtraHelp() {
        return "Prints a count of Trade and Book entries for each Market";
    }


    protected Report getReport() {
        return injector.getInstance(DataSummaryReport.class);
    }

}
