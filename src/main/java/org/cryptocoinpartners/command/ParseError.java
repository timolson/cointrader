package org.cryptocoinpartners.command;

/**
 * @author Tim Olson
 */
public class ParseError extends Error {
    public ParseError(String message) {
        super(message);
    }
}
