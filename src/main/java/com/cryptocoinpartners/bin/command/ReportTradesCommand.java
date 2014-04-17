package com.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameters;


@Parameters(commandNames = "report-trades",commandDescription = "Shows how many trades have been recorded in the database for each MarketListing")
public class ReportTradesCommand extends JpaReportCommand
{
    protected Query getQuery()
    {
        return new Query(
                new String[] {"Market Listing","Num Trades"},
                "select ml, count(*) as num from Trade t, MarketListing ml where t.marketListing=ml group by ml order by num desc"
        );
    }
}
