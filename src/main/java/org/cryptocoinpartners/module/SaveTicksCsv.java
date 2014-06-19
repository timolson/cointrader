package org.cryptocoinpartners.module;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.schema.*;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SuppressWarnings("UnusedDeclaration")
public class SaveTicksCsv
{

    public static List<String> headers = new ArrayList<>(Arrays.asList(new String[]{ "listing", "exchange", "base", "quote", "time", "last", "vol"}));


    @Inject
    public SaveTicksCsv( Context context, Configuration config )
    {
        final String filename = config.getString("savetickscsv.filename");
        if( !StringUtils.isNotBlank(filename) )
            throw new ConfigurationError("You must set the property savetickscsv.filename");
        allowNa = config.getBoolean("savetickscsv.na",false);
        String timeFormatStr = config.getString("savetickscsv.timeFormat","yyMMddHHmmss");
        try {
            timeFormat = new SimpleDateFormat(timeFormatStr);
        }
        catch( NullPointerException e ) {
            throw new ConfigurationError("The output date format must be specified in the property savetickscsv.timeFormat");
        }
        catch( IllegalArgumentException e ) {
            throw new ConfigurationError("The format is invalid: savetickscsv.timeFormat="+timeFormatStr+"\n"+e.getMessage());
        }
        bookDepth = config.getInt("savetickscsv.bookDepth",100);
        for( int i = 0; i < bookDepth; i++ ) {
            int num = i+1;
            headers.add("bidprice"+num);
            headers.add("bidvol"+num);
            headers.add("askprice"+num);
            headers.add("askvol"+num);
        }
        try {
            writer = new CSVWriter(new FileWriter(filename));
            String[] row = new String[headers.size()];
            writer.writeNext(headers.toArray(row));
            writer.flush();
        }
        catch( IOException e ) {
            throw new ConfigurationError("Could not write file "+filename);
        }
    }


    @SuppressWarnings("ConstantConditions")
    @When("select * from Tick")
    public void saveTick( Tick t ) {
        if( !allowNa ) {
            if( t.getLastBook() == null )
                return;
        }

        final Market listing = t.getMarket();
        final String exchange = listing.getExchange().getSymbol();
        final Fungible base = listing.getBase();
        final Fungible quote = listing.getQuote();
        final String timeStr = timeFormat.format(t.getTime().toDate());
        if( t.getPriceCount() != null ) {
            ArrayList<String> row = new ArrayList<>(Arrays.asList(listing.toString(), exchange, base.getSymbol(),
                                                                  quote.getSymbol(), timeStr,
                                                                  String.valueOf(t.getPriceAsDouble()),
                                                                  String.valueOf(t.getVolumeAsDouble())));
            addBookToRow(t, row);
            writer.writeNext(row.toArray(new String[row.size()]));
            try {
                writer.flush();
            }
            catch( IOException e ) {
                log.warn(e.getMessage(), e);
            }
        }
    }


    private void addBookToRow(Tick t, ArrayList<String> row) {
        Book book = t.getLastBook();
        List<Offer> bids = book.getBids();
        List<Offer> asks = book.getAsks();
        for( int i = 0; i < bookDepth; i++ ) {
            if( bids.size() > i ) {
                Offer bid = bids.get(i);
                row.add(String.valueOf(bid.getPriceAsDouble()));
                row.add(String.valueOf(bid.getVolumeAsDouble()));
            }
            else {
                row.add("");
                row.add("");
            }
            if( asks.size() > i ) {
                Offer ask = asks.get(i);
                row.add(String.valueOf(ask.getPriceAsDouble()));
                row.add(String.valueOf(ask.getVolumeAsDouble()));
            }
            else {
                row.add("");
                row.add("");
            }
        }
    }


    @Inject
    public Logger log;
    @Inject
    private Context context;
    @Inject
    private Configuration config;
    private int bookDepth;
    private SimpleDateFormat timeFormat;
    private CSVWriter writer;
    private boolean allowNa;
}
