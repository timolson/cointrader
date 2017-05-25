package org.cryptocoinpartners.command;

import org.cryptocoinpartners.report.Report;
import org.cryptocoinpartners.report.TableOutput;
import org.cryptocoinpartners.util.IoUtil;

/**
 * @author Tim Olson
 */
public abstract class ReportCommand extends CommandBase {

    @Override
    public Object call() {
        IoUtil.outputAscii(runReport(getReport()));
        return true;
    }

    protected TableOutput runReport(Report report) {
        return report.runReport();
    }

    protected abstract Report getReport();

    protected ReportCommand() {
    }
}
