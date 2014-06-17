package org.cryptocoinpartners.command;


/**
 * @author Tim Olson
 */
public interface Command extends Runnable {
    public void printHelp(); // declare a public ConsoleWriter or PrintWriter field to have the output writer injected
    public void parse( String commandArguments );
}