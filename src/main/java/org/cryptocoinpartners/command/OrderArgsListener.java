package org.cryptocoinpartners.command;

import org.antlr.v4.runtime.misc.NotNull;

import javax.inject.Inject;

import static org.cryptocoinpartners.command.Parse.amount;
import static org.cryptocoinpartners.command.Parse.market;


/**
 * @author Tim Olson
 */
public class OrderArgsListener extends OrderBaseListener {

    public void exitStopPrice(@NotNull OrderParser.StopPriceContext ctx) {
        command.setStop(amount(ctx.Amount()));
    }


    public void exitVolume(@NotNull OrderParser.VolumeContext ctx) {
        command.setVolume(amount(ctx.Amount()));
    }


    public void exitLimitPrice(@NotNull OrderParser.LimitPriceContext ctx) {
        command.setLimit(amount(ctx.Amount()));
    }


    public void exitMarket(@NotNull OrderParser.MarketContext ctx) {
        command.setMarket(market(ctx.Market()));
    }


    @Inject
    public OrderCommand command; // this will get injected by AntlrCommandBase.  it must be public
}
