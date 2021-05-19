package org.cryptocoinpartners.bin;

import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.ReadTicksCsv;
import org.cryptocoinpartners.module.SaveMarketData;

import com.beust.jcommander.Parameters;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = { "prefeed", "ticker" }, commandDescription = "Launch a data gathering node")
public class PreFeedDataRunMode extends RunMode {
	@Override
	public void run(Semaphore semaphore) {

		Context context = Context.create();
		context.attach(BasicQuoteService.class);
		context.attach(SaveMarketData.class);
		context.attach(ReadTicksCsv.class);
		if (semaphore != null)
			semaphore.release();
		//System.exit(0);

	}

	@Override
	public void run() {
		Semaphore semaphore = null;
		run(semaphore);
		// TODO Auto-generated method stub

	}
}
