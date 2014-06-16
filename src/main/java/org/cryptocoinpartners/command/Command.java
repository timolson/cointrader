package org.cryptocoinpartners.command;


/**
 * @author Tim Olson
 */
public interface Command extends Runnable {
    public String getCommandName();
    public void parse( String commandArguments );
    void printHelp(); // declare a public PrintWriter field to have the output writer injected
}