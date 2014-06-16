package org.cryptocoinpartners.command;

/**
 * If you implement this method in your ParseTreeListener subclass, you can get access to the AntlrCommand
 *
 * @author Tim Olson
 */
public interface AntlrCommandParseTreeListener {
    public void setCommand( AntlrCommandBase command );
}
