package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;


@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "report-data",commandDescription = "Shows how many trades have been recorded in the database for each Market")
public class ReportDataRunMode extends JpaReportRunMode
{
    protected Query getQuery()
    {
        return new Query(
                new String[] {"Exchange","Num Trades","Num Books"},
                "select ml, count(*) as num, (select count(*) from Book b where b.market=ml) from Trade t, Market ml where t.market=ml group by ml order by num desc"
        );
    }
}
