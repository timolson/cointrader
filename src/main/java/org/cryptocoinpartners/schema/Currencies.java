package org.cryptocoinpartners.schema;

public class Currencies
{
    // Fiat
	public static final Currency AUD = fiat("AUD",0.01);
	public static final Currency CAD = fiat("CAD",0.01);
	public static final Currency CHF = fiat("CHF",0.05);
	public static final Currency CNY = fiat("CNY",0.01);
	public static final Currency EUR = fiat("EUR",0.01);
	public static final Currency GBP = fiat("GBP",0.01);
	public static final Currency HKD = fiat("HKD",0.01);
	public static final Currency JPY = fiat("JPY",1.00);
	public static final Currency MXN = fiat("MXN",0.01);
	public static final Currency NZD = fiat("NZD",0.10);
	public static final Currency RUB = fiat("RUB",0.01);
	public static final Currency SEK = fiat("SEK",1.00);
	public static final Currency SGD = fiat("SGD",0.01);
	public static final Currency TRY = fiat("TRY",0.01);
	public static final Currency USD = fiat("USD",0.01);

    // Cryptos

    // Base coins Bitcoin, Litecoin, Primecoin, Dogecoin, Nextcoin
    // todo review bases!  they may not be correct
    // todo we need a way for a Market to trade in a different basis than the quote's basis
	public static final Currency AUR = crypto("AUR",1e-8);
	public static final Currency BTC = crypto("BTC",1e-8);
	public static final Currency DOGE = crypto("DOGE",1e-8);
	public static final Currency DRK = crypto("DRK",1e-8);
	public static final Currency FTC = crypto("FTC",1e-8);
	public static final Currency LTC = crypto("LTC",1e-8);
	public static final Currency MEM = crypto("MEM",1e-8);
	public static final Currency MOON = crypto("MOON",1e-8);
	public static final Currency NMC = crypto("NMC",1e-8);
    public static final Currency NVC = crypto("NVC",1e-8);
    public static final Currency NXT = crypto("NXT",1e-8);
	public static final Currency XCP = crypto("XCP",1e-8);
	public static final Currency XPM = crypto("XPM",1e-8);


    // Altcoins
    /* these need currency basis research
	public static final Currency FORTY_TWO = crypto("42");
	public static final Currency POINTS = crypto("Points");
	public static final Currency GDC = crypto("GDC");
	public static final Currency CGB = crypto("CGB");
	public static final Currency BTG = crypto("BTG");
	public static final Currency BTE = crypto("BTE");
	public static final Currency BTB = crypto("BTB");
	public static final Currency FST = crypto("FST");
	public static final Currency CASH = crypto("CASH");
	public static final Currency IXC = crypto("IXC");
	public static final Currency KGC = crypto("KGC");
	public static final Currency XJO = crypto("XJO");
	public static final Currency SPT = crypto("SPT");
	public static final Currency LEAF = crypto("LEAF");
	public static final Currency VTC = crypto("VTC");
	public static final Currency LOT = crypto("LOT");
	public static final Currency MEOW = crypto("MEOW");
	public static final Currency BCX = crypto("BCX");
	public static final Currency ARG = crypto("ARG");
	public static final Currency MNC = crypto("MNC");
	public static final Currency COL = crypto("COL");
	public static final Currency PXC = crypto("PXC");
	public static final Currency ZCC = crypto("ZCC");
	public static final Currency CLR = crypto("CLR");
	public static final Currency TRC = crypto("TRC");
	public static final Currency SBC = crypto("SBC");
	public static final Currency ASC = crypto("ASC");
	public static final Currency TIPS = crypto("TIPS");
	public static final Currency ELP = crypto("ELP");
	public static final Currency SRC = crypto("SRC");
	public static final Currency IFC = crypto("IFC");
	public static final Currency PYC = crypto("PYC");
	public static final Currency CMC = crypto("CMC");
	public static final Currency DMD = crypto("DMD");
	public static final Currency TEK = crypto("TEK");
	public static final Currency TIX = crypto("TIX");
	public static final Currency ALF = crypto("ALF");
	public static final Currency PPC = crypto("PPC");
	public static final Currency EMD = crypto("EMD");
	public static final Currency RYC = crypto("RYC");
	public static final Currency CACH = crypto("CACH");
	public static final Currency NRB = crypto("NRB");
	public static final Currency GME = crypto("GME");
	public static final Currency BET = crypto("BET");
	public static final Currency BUK = crypto("BUK");
	public static final Currency GLX = crypto("GLX");
	public static final Currency WDC = crypto("WDC");
	public static final Currency DVC = crypto("DVC");
	public static final Currency RPC = crypto("RPC");
	public static final Currency CNC = crypto("CNC");
	public static final Currency MEC = crypto("MEC");
	public static final Currency FFC = crypto("FFC");
	public static final Currency PHS = crypto("PHS");
	public static final Currency BEN = crypto("BEN");
	public static final Currency FLO = crypto("FLO");
	public static final Currency NAN = crypto("NAN");
	public static final Currency ZET = crypto("ZET");
	public static final Currency NBL = crypto("NBL");
	public static final Currency OSC = crypto("OSC");
	public static final Currency YBC = crypto("YBC");
	public static final Currency QRK = crypto("QRK");
	public static final Currency DEM = crypto("DEM");
	public static final Currency LKY = crypto("LKY");
	public static final Currency MIN = crypto("MIN");
	public static final Currency EAC = crypto("EAC");
	public static final Currency CSC = crypto("CSC");
	public static final Currency ANC = crypto("ANC");
	public static final Currency UTC = crypto("UTC");
	public static final Currency CAT = crypto("CAT");
	public static final Currency MST = crypto("MST");
	public static final Currency CAP = crypto("CAP");
	public static final Currency STR = crypto("STR");
	public static final Currency DGC = crypto("DGC");
	public static final Currency SMC = crypto("SMC");
	public static final Currency ADT = crypto("ADT");
	public static final Currency LK7 = crypto("LK7");
	public static final Currency ELC = crypto("ELC");
	public static final Currency AMC = crypto("AMC");
	public static final Currency MZC = crypto("MZC");
	public static final Currency HBN = crypto("HBN");
	public static final Currency EZC = crypto("EZC");
	public static final Currency FRK = crypto("FRK");
	public static final Currency FLAP = crypto("FLAP");
	public static final Currency CPR = crypto("CPR");
	public static final Currency SXC = crypto("SXC");
	public static final Currency FRC = crypto("FRC");
	public static final Currency TAK = crypto("TAK");
	public static final Currency BQC = crypto("BQC");
	public static final Currency PTS = crypto("PTS");
	public static final Currency RED = crypto("RED");
	public static final Currency TAG = crypto("TAG");
	public static final Currency UNO = crypto("UNO");
	public static final Currency YAC = crypto("YAC");
	public static final Currency NET = crypto("NET");
	public static final Currency ORB = crypto("ORB");
	public static final Currency DBL = crypto("DBL");
	public static final Currency TGC = crypto("TGC");
	public static final Currency XNC = crypto("XNC");
	public static final Currency NEC = crypto("NEC");
	public static final Currency CRC = crypto("CRC");
	public static final Currency GLC = crypto("GLC");
	public static final Currency GLD = crypto("GLD");
	public static final Currency JKC = crypto("JKC");
	public static final Currency MINT = crypto("MINT");
    */

    private static Currency fiat(String symbol, double basis) {
        return Currency.forSymbolOrCreate(symbol, true, basis);
    }
    private static Currency crypto(String symbol, double basis) {
        return Currency.forSymbolOrCreate(symbol, false, basis);
    }
}
