package org.cryptocoinpartners.command;

import org.apache.commons.lang.StringUtils;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class HelpCommand extends CommandBase {

    @Override
    public String getUsageHelp() {
        return "help [command-name]";
    }

    @Override
    public String getExtraHelp() {
        return "Without a command-name, help lists all available commands.  If a command-name is specified, " + "detailed help is given for that command.";
    }

    @Override
    public void parse(String commandArguments) {
        commandName = commandArguments.trim();
    }

    @Override
    public Object call() {
        if (StringUtils.isBlank(commandName)) {
            out.println("Type \"help {command}\" for more detailed information.");
            out.println("Available commands:");
            out.printList(CommandBase.allCommandNames());
        } else {
            Command command = CommandBase.commandForName(commandName, context);
            if (command == null)
                unknownCommand();
            else {
                out.println();
                out.println(command.getUsageHelp());
                out.println();
                out.printLinesWrapped("    ", command.getExtraHelp());
                out.println();
            }
        }
        return true;
    }

    private void unknownCommand() {
        out.println("Unknown command " + commandName + ".  Available commands:");
        out.printList(CommandBase.allCommandNames());
    }

    private String commandName;
}
