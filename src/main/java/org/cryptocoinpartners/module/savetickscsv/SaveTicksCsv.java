package org.cryptocoinpartners.module.savetickscsv;

import au.com.bytecode.opencsv.CSVWriter;
import org.cryptocoinpartners.module.ConfigurationError;
import org.cryptocoinpartners.module.Esper;
import org.cryptocoinpartners.module.ModuleListenerBase;
import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.Fungible;
import org.cryptocoinpartners.schema.MarketListing;
import org.cryptocoinpartners.schema.Tick;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;


@SuppressWarnings("UnusedDeclaration")
public class SaveTicksCsv extends ModuleListenerBase
{

    public static String[] headers = new String[] { "listing", "market", "base", "quote", "time", "last", "vol", "bid", "bidvol", "ask", "askvol" };


    public void initModule( Esper esper, Configuration config )
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
        try {
            writer = new CSVWriter(new FileWriter(filename));
            writer.writeNext(headers);
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
            if( t.getBestAsk() == null && t.getBestBid() == null
                        || t.getPriceCount() == null )
                return;
        }

        final MarketListing listing = t.getMarketListing();
        final String market = listing.getMarket().getSymbol();
        final Fungible base = listing.getBase();
        final Fungible quote = listing.getQuote();
        final String timeStr = timeFormat.format(t.getTime().toDate());
        final String bid = t.getBestBid() == null ? "" : String.valueOf(t.getBestBid().getPriceAsDouble());
        final String bidVol = t.getBestBid() == null ? "" : String.valueOf(t.getBestBid().getVolumeAsDouble());
        final String ask = t.getBestAsk() == null ? "" : String.valueOf(t.getBestAsk().getPriceAsDouble());
        final String askVol = t.getBestAsk() == null ? "" : String.valueOf(t.getBestAsk().getVolumeAsDouble());
        if( t.getPriceCount() != null ) {
            writer.writeNext(new String[] {
                                     listing.toString(),
                                     market,
                                     base.getSymbol(),
                                     quote.getSymbol(),
                                     timeStr,
                                     String.valueOf(t.getPriceAsDouble()),
                                     String.valueOf(t.getVolumeAsDouble()),
                                     bid,
                                     bidVol,
                                     ask,
                                     askVol
                             }
            );
            try {
                writer.flush();
            }
            catch( IOException e ) {
                log.warn(e.getMessage(), e);
            }
        }
    }


    public void destroyModule()
    {
        try {
            writer.close();
        }
        catch( IOException e ) {
            log.error(e.getMessage(), e);
        }
        super.destroyModule();
    }


    private SimpleDateFormat timeFormat;
    private CSVWriter writer;
    private boolean allowNa;
}
