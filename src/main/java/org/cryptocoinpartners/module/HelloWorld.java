package org.cryptocoinpartners.module;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Event;
import org.slf4j.Logger;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;

/**
 * @author Tim Olson
 */
@Singleton
public class HelloWorld {

	@When("select * from Event")
	public void doSomethingWithEvery(Event e) {
		log.info("Hello, Event " + (++count) + " " + e);
		getAvgTrade();
	}

	static int count = 0;

	public void getAvgTrade() {

		List<Object> events = null;

		try {
			events = context.loadStatementByName("getAvgTrade");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DeploymentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (events.size() > 0) {
			Double avgTrade = ((Double) events.get(events.size() - 1));
			log.info("Hello, Event " + avgTrade);
			// return(trade.getPrice());
		}

	}

	private double avgTrade;
	@Inject
	private Logger log;
	@Inject
	protected Context context;

}
