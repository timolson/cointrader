package org.cryptocoinpartners.command;

import jline.ConsoleReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collection;


/**
 * @author Tim Olson
 */
public class ConsoleWriter extends PrintWriter {

    public ConsoleWriter(ConsoleReader out) {
        super(new ConsolePrintWriter(out));
    }


    public void printList( Collection items ) {
        for( Object item : items )
            println("\t• "+item);
    }


    private static class ConsolePrintWriter extends Writer {
        @SuppressWarnings("NullableProblems")
        public void write(char[] cbuf, int off, int len) throws IOException {
            String output = new String(cbuf, off, len);
            try {
                console.printString(output);
            }
            catch( IOException e ) {
                throw new Error("Could not write to console",e);
            }
        }


        public void flush() throws IOException {
            console.flushConsole();
        }
        public void close() throws IOException {
            console.flushConsole();
        }
        private ConsolePrintWriter(ConsoleReader console) { this.console = console; }

        private ConsoleReader console;
    }
}
