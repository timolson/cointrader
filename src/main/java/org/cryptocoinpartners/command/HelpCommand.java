package org.cryptocoinpartners.command;

import org.apache.commons.lang.StringUtils;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class HelpCommand extends CommandBase {

    public void parse(String commandArguments) {
        commandName = commandArguments.trim();
    }


    public void printHelp() {
        out.println("help [command-name]");
        out.println();
        out.println("\tWithout a command-name, help lists all available commands.");
        out.println("\tIf a command-name is specified, detailed help is given for that command");
    }


    public void run() {
        if( StringUtils.isBlank(commandName) ) {
            out.println("Available commands:");
            out.printList(CommandBase.allCommandNames());
        }
        else {
            Command command = CommandBase.commandForName(commandName,context);
            if( command == null )
                unknownCommand();
            else {
                command.printHelp();
            }
        }
    }


    private void unknownCommand() {
        out.println("Unknown command "+commandName+".  Available commands:");
        out.printList(CommandBase.allCommandNames());
    }


    private String commandName;
}
