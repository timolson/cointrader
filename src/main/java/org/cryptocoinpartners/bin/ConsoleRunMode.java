package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;
import jline.Completor;
import jline.ConsoleReader;
import jline.History;
import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.command.Command;
import org.cryptocoinpartners.command.CommandBase;
import org.cryptocoinpartners.command.ConsoleWriter;
import org.cryptocoinpartners.command.ParseError;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.BasicAccountService;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.TickWindow;
import org.cryptocoinpartners.module.xchangedata.XchangeData;
import org.cryptocoinpartners.schema.Fund;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = {"console","terminal"}, commandDescription = "run in interactive mode")
public class ConsoleRunMode extends RunMode {

    public void run() {
        try {
            init();
            //noinspection InfiniteLoopStatement
            while(true) {
                String line = console.readLine();
                if( StringUtils.isEmpty(line) )
                    continue;
                Matcher matcher = Pattern.compile("^(\\w+)(\\s+.*)?$").matcher(line);
                if( !matcher.matches() ) {
                    out.println("Could not understand command name");
                    continue;
                }
                String commandName = matcher.group(1);
                Command command;
                try {
                    command = CommandBase.commandForName(commandName, context);
                }
                catch( Throwable e ) {
                    log.warn("Could not create command "+commandName,e);
                    internalError();
                    continue;
                }
                if( command == null ) {
                    out.println("Unknown command " + commandName + ".  Available commands:");
                    out.printList(CommandBase.allCommandNames());
                    continue;
                }
                String argStr = matcher.group(2);
                if( argStr == null )
                    argStr = "";
                try {
                    command.parse(argStr);
                }
                catch( ParseError e ) {
                    out.println(e.getMessage());
                }
                catch( Throwable e ) {
                    log.warn("Could not parse command " + commandName, e);
                }
                try {
                    command.run();
                    history.addToHistory(line);
                }
                catch( Throwable e ) {
                    log.warn("Could not run command "+commandName,e);
                    internalError();
                }
            }
        }
        catch( IOException e ) {
            throw new Error("Console exception",e);
        }
    }


    private void internalError() {
        out.println("Internal error: see cointrader.log");
    }


    private void init() throws IOException {
        context = Context.create();

        context.attach(XchangeData.class);
        context.attach(TickWindow.class);
        context.attach(BasicQuoteService.class);
        context.attach(BasicAccountService.class);
        context.attach(MockOrderService.class);

        console = new ConsoleReader();
        String prompt = config.getString("console.cursor","ct>");
        console.setDefaultPrompt(prompt);
        history = new History();
        history.setMaxSize(config.getInt("console.hisory.size",100));
        console.setHistory(history);
        console.setUseHistory(true);
        out = new ConsoleWriter(console);
        context.attach(ConsoleWriter.class,out);
        context.attach(PrintWriter.class,out);
        out.println("Coin Trader Console "+config.getString("project.version"));
    }


    private Fund fund;
    private Context context;
    private ConsoleReader console;
    private ConsoleWriter out;
    private History history;
}
