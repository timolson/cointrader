package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.report.AdHocJpaReport;
import org.cryptocoinpartners.report.Report;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "report-jpa",separators = "",commandDescription = "interprets the command-line args as a JPA query")
public class AdHocJpaReportRunMode extends ReportRunMode
{

    protected Report getReport() {
        Iterator<String> quotedStringIter = new Iterator<String>() {
            private Iterator<String> queryIter = query.iterator();
            public boolean hasNext() { return queryIter.hasNext(); }
            public void remove() { throw new Error("Unimplemented"); }
            public String next() {
                String next = queryIter.next();
                return next.matches(".*\\s.*") ? '\'' + next + '\'' : next;
            }
        };
        final String queryStr = StringUtils.join(quotedStringIter, " ");
        AdHocJpaReport report = injector.getInstance(AdHocJpaReport.class);
        report.setQueryString(queryStr);
        return report;
    }


    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter
    private final List<String> query = new ArrayList<>();
}
