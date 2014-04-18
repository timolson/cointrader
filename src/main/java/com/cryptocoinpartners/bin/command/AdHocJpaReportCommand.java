package com.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;


@Parameters(commandNames = "report-jpa",commandDescription = "interprets the command-line args as a JPA query")
public class AdHocJpaReportCommand extends JpaReportCommand
{
    protected Query getQuery()
    {
        final String queryStr = StringUtils.join(query, " ");
        return new Query(null,queryStr);
    }


    @Parameter
    private List<String> query = new ArrayList<String>();
}
