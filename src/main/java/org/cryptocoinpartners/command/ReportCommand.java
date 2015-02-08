package org.cryptocoinpartners.command;

import org.cryptocoinpartners.report.TableOutput;
import org.cryptocoinpartners.util.IoUtil;
import org.cryptocoinpartners.report.Report;


/**
 * @author Tim Olson
 */
public abstract class ReportCommand extends CommandBase {

    @Override
    public void run() {
        IoUtil.outputAscii(runReport(getReport()));
    }


    protected TableOutput runReport(Report report) {
        return report.runReport();
    }


    protected abstract Report getReport();


    protected ReportCommand() { }
}
