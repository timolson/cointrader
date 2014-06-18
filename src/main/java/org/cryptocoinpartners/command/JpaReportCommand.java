package org.cryptocoinpartners.command;

import org.cryptocoinpartners.report.AdHocJpaReport;
import org.cryptocoinpartners.report.Report;
import org.cryptocoinpartners.report.TableOutput;


/**
 * @author Tim Olson
 */
@CommandName("jpa")
public class JpaReportCommand extends ReportCommand {

    public void printHelp() {
        out.println("jpa {jpa_query}");
        out.println();
        out.println("\tRuns the specified ad-hoc query against the database and prints");
        out.println("\tthe result as a table");
    }


    public void parse(String commandArguments) {
        queryStr = commandArguments;
    }


    protected Report getReport() {
        AdHocJpaReport jpaReport = injector.getInstance(AdHocJpaReport.class);
        jpaReport.setQueryString(queryStr);
        return jpaReport;
    }


    protected TableOutput runReport(Report report) {
        try {
            return super.runReport(report);
        }
        catch( IllegalArgumentException e ) {
            out.println(e.getMessage());
            return null;
        }
    }


    private String queryStr;
}
