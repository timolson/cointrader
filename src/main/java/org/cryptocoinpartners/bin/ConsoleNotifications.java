package org.cryptocoinpartners.bin;

import org.cryptocoinpartners.command.ConsoleWriter;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.slf4j.Logger;

import javax.inject.Inject;


/**
 * This is attached to the Context operated by ConsoleRunMode.  It is responsible for printing event alerts to the
 * console.
 *
 * @author Tim Olson
 */
public class ConsoleNotifications {


    @When("select * from Fill")
    public void announceFill( Fill f ) {
        writer.println("Filled order "+f.getOrder().getId()+": "+f);
        writer.flush();
    }


    @When("select * from OrderUpdate")
    public void announceUpdate( OrderUpdate update ) {
        writer.println("Order "+update.getOrder().getId()+" status: "+update.getState());
        writer.flush();
    }


    @Inject
    ConsoleWriter writer;
    @Inject
    private Logger logger;
    @Inject
    private Context context;
}
