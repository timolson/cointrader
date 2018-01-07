package org.cryptocoinpartners.bin;

import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.SaveMarketData;
import org.cryptocoinpartners.module.xchange.XchangeData;

import com.beust.jcommander.Parameters;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = { "save-data", "ticker" }, commandDescription = "Launch a data gathering node")
public class SaveDataRunMode extends RunMode {

	@Override
	public void run(Semaphore semaphore) {
		Context context = Context.create();
		context.attach(BasicQuoteService.class);
		context.attach(SaveMarketData.class);
		context.attach(XchangeData.class);

		if (semaphore != null)
			semaphore.release();
	}

	@Override
	public void run() {
		Semaphore semaphore = null;
		run(semaphore);

	}
}
