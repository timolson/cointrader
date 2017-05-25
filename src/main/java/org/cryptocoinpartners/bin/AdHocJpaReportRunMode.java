package org.cryptocoinpartners.bin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.report.AdHocJpaReport;
import org.cryptocoinpartners.report.Report;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "report-jpa", separators = "", commandDescription = "interprets the command-line args as a JPA query")
public class AdHocJpaReportRunMode extends ReportRunMode {

    @Override
    protected Report getReport() {
        Iterator<String> quotedStringIter = new Iterator<String>() {
            private final Iterator<String> queryIter = query.iterator();

            @Override
            public boolean hasNext() {
                return queryIter.hasNext();
            }

            @Override
            public void remove() {
                throw new Error("Unimplemented");
            }

            @Override
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
