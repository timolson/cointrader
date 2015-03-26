package org.cryptocoinpartners.bin;

import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.report.Report;
import org.cryptocoinpartners.report.TableOutput;
import org.cryptocoinpartners.util.IoUtil;

import com.beust.jcommander.Parameter;

public abstract class ReportRunMode extends RunMode {
    @Override
    public void run(Semaphore semaphore) {
        output(getReport().runReport());
        if (semaphore != null)
            semaphore.release();
    }

    @Override
    public void run() {
        Semaphore semaphore = null;
        run(semaphore);

    }

    protected void output(TableOutput tableOutput) {
        if (csv != null)
            IoUtil.writeCsv(tableOutput, csv);
        else
            IoUtil.outputAscii(tableOutput);
    }

    protected abstract Report getReport();

    @Parameter(names = "-csv", description = "specifies a file for output in CSV format")
    private final String csv = null;
}
