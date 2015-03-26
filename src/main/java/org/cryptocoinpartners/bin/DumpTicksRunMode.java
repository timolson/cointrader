package org.cryptocoinpartners.bin;

import java.util.List;
import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.util.IoUtil;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "dump-ticks", commandDescription = "generate ticks into a csv file")
public class DumpTicksRunMode extends RunMode {

    @Override
    public void run(Semaphore semaphore) {
        String startString = startStr;
        String endString = endStr;
        IoUtil.dumpTicks(filenames.get(0), startString, endString, allowNa);
        if (semaphore != null)
            semaphore.release();
        System.exit(0);
    }

    @Parameter(names = { "-start" }, description = "English time description of the time to start dumping ticks")
    public String startStr = null;

    @Parameter(names = { "-end" }, description = "English time description of the time to stop dumping ticks")
    public String endStr = null;

    @Parameter(names = "-na", description = "If set, any ticks which are missing data (no Book or last Trade) will still be output")
    public boolean allowNa = false;

    @Parameter(required = true, arity = 1, description = "output filename")
    public List<String> filenames;

    @Override
    public void run() {
        Semaphore semaphore = null;
        run(semaphore);

    }
}
