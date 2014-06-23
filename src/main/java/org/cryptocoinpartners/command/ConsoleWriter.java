package org.cryptocoinpartners.command;


import jline.console.ConsoleReader;
import org.apache.commons.lang.WordUtils;

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
        this.console = out;
    }


    public void printList( Collection items ) {
        printLines("\t• ", items);
    }


    /**
     * @param prefix prepended to each line
     * @param multiline a long string to be wrapped across lines
     */
    public void printLinesWrapped( String prefix, String multiline ) {
        multiline = multiline.replaceAll("[\\r\\n]","");
        int availableWidth = console.getTerminal().getWidth() - prefix.length();
        String wrapped = WordUtils.wrap(multiline, availableWidth);
        for( String line : wrapped.split("\\n") )
        try {
            console.println(prefix+line);
        }
        catch( IOException e ) {
            throw new Error(e);
        }
    }


    public void printLines(String prefix, Collection lineItems) {
        for( Object item : lineItems )
            println(prefix+item);
    }


    private static class ConsolePrintWriter extends Writer {
        @SuppressWarnings("NullableProblems")
        public void write(char[] cbuf, int off, int len) throws IOException {
            String output = new String(cbuf, off, len);
            try {
                console.print(output);
                if( output.contains("\n") )
                    console.flush();
            }
            catch( IOException e ) {
                throw new Error("Could not write to console",e);
            }
        }


        public void flush() throws IOException { console.flush(); }
        public void close() throws IOException { console.flush(); }


        private ConsolePrintWriter(ConsoleReader console) { this.console = console; }


        // this redundancy is necessary to make the super() constructor work with a static inner class
        private ConsoleReader console;
    }


    private ConsoleReader console;
}
