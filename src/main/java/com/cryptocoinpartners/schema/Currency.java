package com.cryptocoinpartners.schema;


/**
 * @author Tim Olson
 */
public enum Currency {
    // Fiat
    USD, RMB, EUR, JPY, GBP, // TODO: add more

    // Cryptos
    // Base coins Bitcoin, Litecoin, Primecoin
    BTC,LTC,XPM,
    // Altcoins
    FORTY_TWO("42"),POINTS("Points"),GDC,CGB,BTG,BTE,BTB,FST,CASH,DOGE,IXC,FTC,KGC,XJO,SPT,LEAF,VTC,LOT,MEOW,BCX,ARG,
    MNC,COL,PXC,MOON,ZCC,CLR,TRC,SBC,ASC,TIPS,ELP,SRC,IFC,PYC,CMC,DMD,TEK,TIX,ALF,PPC,EMD,RYC,CACH,NRB,GME,BET,BUK,GLX,
    WDC,DVC,MEM,RPC,CNC,MEC,FFC,PHS,NXT,BEN,FLO,NAN,ZET,NBL,AUR,OSC,YBC,QRK,DEM,LKY,MAX,EAC,CSC,ANC,UTC,CAT,MST,CAP,
    STR,DGC,SMC,ADT,LK7,ELC,AMC,MZC,HBN,EZC,FRK,NVC,FLAP,CPR,SXC,FRC,NMC,TAK,BQC,PTS,RED,TAG,UNO,YAC,NET,ORB,DBL,DRK,
    TGC,XNC,NEC,CRC,GLC,GLD,JKC,MINT;

    public static Currency forSymbol(String symbol) {
        try {
            return Currency.valueOf(symbol);
        }
        catch( IllegalArgumentException e ) {
            if( symbol.equals(FORTY_TWO.getSymbol()) )
                return FORTY_TWO;
            if( symbol.equals(POINTS.getSymbol()) )
                return POINTS;
            throw e;
        }
    }

    public String getSymbol() { return symbol; }
    public String toString() { return symbol; }

    private Currency() { symbol = this.name(); }
    private Currency(String s) { symbol = s; }
    private String symbol;
}
