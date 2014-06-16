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
import org.cryptocoinpartners.util.Config;

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
                Command command = CommandBase.commandForName(commandName,context);
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
                }
                catch( Throwable e ) {
                    log.warn("Could not run command "+commandName,e);
                    out.println("Internal error: see cointrader.log");
                }
            }
        }
        catch( IOException e ) {
            throw new Error("Console exception",e);
        }
    }


    private void init() throws IOException {
        context = new Context();

        context.attach(XchangeData.class);
        context.attach(TickWindow.class);
        context.attach(BasicQuoteService.class);
        context.attach(BasicAccountService.class);
        context.attach(MockOrderService.class);

        console = new ConsoleReader();
        String prompt = Config.combined().getString("console.cursor","ct>");
        console.setDefaultPrompt(prompt);
        console.setHistory(new History());
        console.setUseHistory(true);
        console.addCompletor(new Completor() {
            public int complete(String s, int i, List list) {
                return 0;
            }
        });
        out = new ConsoleWriter(console);
        context.attach(ConsoleWriter.class,out);
        context.attach(PrintWriter.class,out);
    }


    private Fund fund;
    private Context context;
    private ConsoleReader console;
    private ConsoleWriter out;
}
