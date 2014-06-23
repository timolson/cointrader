package org.cryptocoinpartners.command;

import org.antlr.v4.runtime.misc.NotNull;

import javax.inject.Inject;

import java.math.BigDecimal;

import org.cryptocoinpartners.command.Parse;
import org.cryptocoinpartners.schema.Market;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class OrderArgsListener extends OrderBaseListener {

    public void exitStopPrice(@NotNull OrderParser.StopPriceContext ctx) {
        BigDecimal amount = Parse.amount(ctx.Amount());
        command.setStop(amount);
    }


    public void exitVolume(@NotNull OrderParser.VolumeContext ctx) {
        BigDecimal volume = Parse.amount(ctx.Amount());
        command.setVolume(volume);
    }


    public void exitLimitPrice(@NotNull OrderParser.LimitPriceContext ctx) {
        BigDecimal limit = Parse.amount(ctx.Amount());
        command.setLimit(limit);
    }


    public void exitMarket(@NotNull OrderParser.MarketContext ctx) {
        Market market = Parse.market(ctx.Market());
        command.setMarket(market);
    }


    @Inject
    private OrderCommand command; // this gets injected by AntlrCommandBase.
}
