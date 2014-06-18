package org.cryptocoinpartners.command;


import javax.annotation.Nullable;


/**
 * @author Tim Olson
 */
public interface Command extends Runnable {
    /** This should return the title of the help page, something like "mycmd [option1|opt2] {filename}".  If null, then
     * the command is assumed to have no arguments and just the command name is printed. */
    @Nullable
    public String getUsageHelp();


    /** this should return the body of the help page, something like "mycmd manipulates the filename unless option1 or opt2 are set..." */
    public String getExtraHelp();


    /** this is called before run()
     * @param commandArguments the remainder of the command-line after the command name has been removed from the front */
    public void parse( String commandArguments ) throws ParseError;
}