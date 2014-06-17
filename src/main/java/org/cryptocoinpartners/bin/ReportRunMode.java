package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameter;
import org.cryptocoinpartners.report.Report;
import org.cryptocoinpartners.util.IoUtil;
import org.cryptocoinpartners.report.TableOutput;


public abstract class ReportRunMode extends RunMode
{
    public void run()
    {
        output(getReport().runReport());
    }


    protected void output( TableOutput tableOutput )
    {
        if( csv != null )
            IoUtil.writeCsv(tableOutput, csv);
        else
            IoUtil.outputAscii(tableOutput);
    }



    protected abstract Report getReport();


    @Parameter(names = "-csv", description = "specifies a file for output in CSV format")
    private String csv = null;
}
