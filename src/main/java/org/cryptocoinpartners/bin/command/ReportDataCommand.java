package org.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameters;


@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "report-data",commandDescription = "Shows how many trades have been recorded in the database for each MarketListing")
public class ReportDataCommand extends JpaReportCommand
{
    protected Query getQuery()
    {
        return new Query(
                new String[] {"Market Listing","Num Trades","Num Books"},
                "select ml, count(*) as num, (select count(*) from Book b where b.marketListing=ml) from Trade t, MarketListing ml where t.marketListing=ml group by ml order by num desc"
        );
    }
}
