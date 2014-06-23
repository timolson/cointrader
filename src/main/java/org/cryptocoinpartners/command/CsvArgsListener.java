package org.cryptocoinpartners.command;

import org.antlr.v4.runtime.misc.NotNull;

import javax.inject.Inject;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class CsvArgsListener extends CsvBaseListener {

    public void exitStartDate(@NotNull CsvParser.StartDateContext ctx) {
        command.startDate = ctx.getText();
    }


    public void exitTickDuration(@NotNull CsvParser.TickDurationContext ctx) {
        command.endDate = ctx.getText();
    }


    public void exitFilename(@NotNull CsvParser.FilenameContext ctx) {
        command.filename = ctx.getText();
    }


    @Inject
    private CsvCommand command;
}
