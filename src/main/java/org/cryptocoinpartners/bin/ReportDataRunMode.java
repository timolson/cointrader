package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.report.DataSummaryReport;
import org.cryptocoinpartners.report.Report;


@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = {"data", "data-summary", "report-data"},
            commandDescription = "Shows how many trades have been recorded in the database for each Market")
public class ReportDataRunMode extends ReportRunMode {
    protected Report getReport() {
        return injector.getInstance(DataSummaryReport.class);
    }
}