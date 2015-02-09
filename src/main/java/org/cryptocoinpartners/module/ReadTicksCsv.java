package org.cryptocoinpartners.module;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Trade;
import org.joda.time.Instant;
import org.slf4j.Logger;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.bean.ColumnPositionMappingStrategy;
import au.com.bytecode.opencsv.bean.CsvToBean;

import com.xeiam.xchange.currency.CurrencyPair;

@SuppressWarnings("UnusedDeclaration")
@Singleton
public class ReadTicksCsv {

    public static List<String> headers = new ArrayList<>(
            Arrays.asList(new String[] { "listing", "exchange", "base", "quote", "prompt", "time", "last", "vol" }));

    private SimpleDateFormat timeFormat;
    private CSVReader reader;
    private List csvEntries;
    private final boolean allowNa;
    private Exchange exchange;
    private Listing listing;
    private Market market;
    private CurrencyPair pair;
    private final Book.Builder bookBuilder = new Book.Builder();
    private final SaveMarketData dbPersistance = new SaveMarketData();;

    @Inject
    public ReadTicksCsv(Context context, Configuration config) {
        final String filename = config.getString("readtickscsv.filename");
        if (!StringUtils.isNotBlank(filename))
            throw new ConfigurationError("You must set the property readtickscsv.filename");
        allowNa = config.getBoolean("readtickscsv.na", false);
        String timeFormatStr = config.getString("readtickscsv.timeFormat", "yyMMddHHmmss");
        try {
            timeFormat = new SimpleDateFormat(timeFormatStr);
        } catch (NullPointerException e) {
            throw new ConfigurationError("The input date format must be specified in the property readtickscsv.timeFormat");
        } catch (IllegalArgumentException e) {
            throw new ConfigurationError("The format is invalid: readtickscsv.timeFormat=" + timeFormatStr + "\n" + e.getMessage());
        }

        try {
            reader = new CSVReader(new FileReader(filename), ',', '\"', 1);
            //          ColumnPositionMappingStrategy<CsvTrade> strat = new ColumnPositionMappingStrategy();
            //          strat.setType(CsvTrade.class);
            //          String[] columns = new String[] { "market", "time", "price", "volume" }; // the fields to bind do in your JavaBean
            //          strat.setColumnMapping(columns);
            //
            //          CsvToBean csv = new CsvToBean();
            //          List list = csv.parse(strat, reader);
            //          //
            ColumnPositionMappingStrategy<CsvTrade> mappingStrategy = new ColumnPositionMappingStrategy<CsvTrade>();
            mappingStrategy.setType(CsvTrade.class);

            // the fields to bind do in your JavaBean
            String[] columns = new String[] { "listing", "exchange", "base", "quote", "prompt", "time", "last", "vol", "bidprice1", "bidvol1", "askprice1",
                    "askvol1" };

            mappingStrategy.setColumnMapping(columns);

            CsvToBean<CsvTrade> csv = new CsvToBean<CsvTrade>();
            List<CsvTrade> trades = csv.parse(mappingStrategy, reader);
            Iterator<CsvTrade> it = trades.iterator();
            while (it.hasNext()) {
                CsvTrade csvtrade = it.next();
                Instant instant = new Instant(csvtrade.getTime());
                if (exchange == null || !exchange.toString().equals(csvtrade.getExchange())) {
                    exchange = Exchange.forSymbolOrCreate(csvtrade.getExchange());
                    market = null;
                }
                if (listing == null || !listing.toString().equals(csvtrade.getListingAsString())) {

                    listing = Listing.forSymbol(csvtrade.getListingAsString());
                    market = null;
                }
                if (pair == null || !pair.toString().equals(csvtrade.getCurrencyPair())) {
                    pair = new CurrencyPair(csvtrade.getBase(), csvtrade.getQuote());
                    market = null;
                }

                if (market == null)
                    market = Market.findOrCreate(exchange, listing);

                Trade trade = Trade.fromDoubles(market, instant, instant, csvtrade.getTime().toString(), csvtrade.getLast(), csvtrade.getVol());
                bookBuilder.start(instant, instant, csvtrade.getTime().toString(), market);
                bookBuilder.addBid(BigDecimal.valueOf(csvtrade.getBidprice1()), BigDecimal.valueOf(csvtrade.getBidvol1()));
                bookBuilder.addAsk(BigDecimal.valueOf(csvtrade.getAskprice1()), BigDecimal.valueOf(csvtrade.getAskvol1()));
                // bookBuilder.
                // bookBuilder.
                Book book = bookBuilder.build();
                //TODO create insertion and deltion blobs from book.
                context.publish(book);
                context.publish(trade);

            }

        } catch (IOException e) {
            throw new ConfigurationError("Could not read file " + filename);
        }
    }

    public static class CsvTrade {

        private String exchange;
        private String listing;
        private Long time;
        private double last;
        private double vol;
        private String base;
        private String quote;
        private String prompt;
        private double bidprice1;
        private double bidvol1;
        private double askprice1;
        private double askvol1;

        public String getExchange() {
            return exchange;
        }

        public String getListingAsString() {
            if ((getPrompt()) == null)
                return base + "." + quote;
            return base + "." + quote + "." + prompt;

        }

        public String getBase() {
            return base;
        }

        public String getPrompt() {
            if (prompt.isEmpty())
                return null;
            return prompt;
        }

        public String getQuote() {
            return quote;
        }

        public String getCurrencyPair() {
            // TODO Auto-generated method stub
            return base + "/" + quote;
        }

        public String getListing() {
            return listing;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public void setListing(String listing) {
            this.listing = listing;
        }

        public Long getTime() {
            return time;
        }

        public void setTime(Long time) {
            this.time = time;
        }

        public void setBase(String base) {
            this.base = base;
        }

        public void setQuote(String quote) {
            this.quote = quote;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public double getLast() {
            return last;
        }

        public void setLast(double last) {
            this.last = last;
        }

        public double getBidprice1() {
            return bidprice1;
        }

        public void setBidprice1(double bidprice1) {
            this.bidprice1 = bidprice1;
        }

        public double getBidvol1() {
            return bidvol1;
        }

        public void setBidvol1(double bidvol1) {
            this.bidvol1 = bidvol1;
        }

        public double getAskprice1() {
            return askprice1;
        }

        public void setAskprice1(double askprice1) {
            this.askprice1 = askprice1;
        }

        public double getAskvol1() {
            return askvol1;
        }

        public void setAskvol1(double askvol1) {
            this.askvol1 = askvol1;
        }

        public double getVol() {
            return vol;
        }

        public void setVol(double vol) {
            this.vol = vol;
        }

    }

    @Inject
    public Logger log;
}
