package org.cryptocoinpartners.command;

import org.cryptocoinpartners.report.DataSummaryReport;
import org.cryptocoinpartners.report.Report;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class DataCommand extends ReportCommand {


    @Override
    public String getUsageHelp() {
        return "data [summary]";
    }

    @Override
    public String getExtraHelp() {
        return "Prints a count of Trade and Book entries for each Market";
    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    protected Report getReport() {
        return injector.getInstance(DataSummaryReport.class);
    }

}
