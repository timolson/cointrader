package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "report-jpa",separators = "",commandDescription = "interprets the command-line args as a JPA query")
public class AdHocJpaReportRunMode extends JpaReportRunMode
{
    protected Query getQuery()
    {
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
        return new Query(null,queryStr);
    }


    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter
    private final List<String> query = new ArrayList<String>();
}
