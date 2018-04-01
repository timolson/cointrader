package org.cryptocoinpartners.schema;

public class Currencies {
	/**
	   * 
	   */
	// Fiat
	public static final Currency AUD = fiat("AUD", 0.01);
	public static final Currency CAD = fiat("CAD", 0.01);
	public static final Currency CHF = fiat("CHF", 0.05);
	public static final Currency CNY = fiat("CNY", 0.01);
	public static final Currency EUR = fiat("EUR", 0.01);
	public static final Currency GBP = fiat("GBP", 0.01);
	public static final Currency HKD = fiat("HKD", 0.01);
	public static final Currency JPY = fiat("JPY", 1.00);
	public static final Currency MXN = fiat("MXN", 0.01);
	public static final Currency NZD = fiat("NZD", 0.10);
	public static final Currency RUB = fiat("RUB", 0.01);
	public static final Currency SEK = fiat("SEK", 1.00);
	public static final Currency SGD = fiat("SGD", 0.01);
	public static final Currency TRY = fiat("TRY", 0.01);
	public static final Currency USD = fiat("USD", 0.01);

	// Cryptos

	// Base coins Bitcoin, Litecoin, Primecoin, Dogecoin, Nextcoin
	// todo review bases!  they may not be correct
	// todo we need a way for a Market to trade in a different basis than the quote's basis
	public static final Currency AUR = crypto("AUR", 1e-8);
	public static final Currency BTC = crypto("BTC", 1e-8);
	public static final Currency BTS = crypto("BTS", 1e-8);
	public static final Currency DOGE = crypto("DOGE", 1e-8);
	public static final Currency DRK = crypto("DRK", 1e-7);
	public static final Currency ETC = crypto("ETC", 1e-8);
	public static final Currency FTC = crypto("FTC", 1e-8);
	public static final Currency LTC = crypto("LTC", 1e-8);
	public static final Currency MEM = crypto("MEM", 1e-8);
	public static final Currency MOON = crypto("MOON", 1e-8);
	public static final Currency NEO = crypto("NEO", 1e-8);
	public static final Currency NMC = crypto("NMC", 1e-8);
	public static final Currency NVC = crypto("NVC", 1e-8);
	public static final Currency NXT = crypto("NXT", 1e-8);
	public static final Currency XCP = crypto("XCP", 1e-8);
	public static final Currency XPM = crypto("XPM", 1e-8);
	public static final Currency XRP = crypto("XRP", 1e-8);
	public static final Currency ZEC = crypto("ZEC", 1e-8);
	
	// Altcoins
	// todo review bases!  they may not be correct
	public static final Currency ABY = crypto("ABY", 1e-8);
	public static final Currency ADA = crypto("ADA", 1e-8);
	public static final Currency ADT = crypto("ADT", 1e-8);
	public static final Currency ADX = crypto("ADX", 1e-8);
	public static final Currency AEON = crypto("AEON", 1e-8);
	public static final Currency AGRS = crypto("AGRS", 1e-8);
	public static final Currency AMP = crypto("AMP", 1e-8);
	public static final Currency ANT = crypto("ANT", 1e-8);
	public static final Currency APX = crypto("APX", 1e-8);
	public static final Currency ARDR = crypto("ARDR", 1e-8);
	public static final Currency ARK = crypto("ARK", 1e-8);
	public static final Currency BAT = crypto("BAT", 1e-8);
	public static final Currency BAY = crypto("BAY", 1e-8);
	public static final Currency BC = crypto("BC", 1e-8);
	public static final Currency BCC = crypto("BCC", 1e-8);
	public static final Currency BCH = crypto("BCH", 1e-8);
	public static final Currency BCU = crypto("BCU", 1e-8);
	public static final Currency BCY = crypto("BCY", 1e-8);
	public static final Currency BFX = crypto("BFX", 1e-8);
	public static final Currency BITB = crypto("BITB", 1e-8);
	public static final Currency BLITZ = crypto("BLITZ", 1e-8);
	public static final Currency BLK = crypto("BLK", 1e-8);
	public static final Currency BLOCK = crypto("BLOCK", 1e-8);
	public static final Currency BNB = crypto("BNB", 1e-8);
	public static final Currency BNT = crypto("BNT", 1e-8);
	public static final Currency BQC = crypto("BQC", 1e-8);
	public static final Currency BRK = crypto("BRK", 1e-8);
	public static final Currency BRX = crypto("BRX", 1e-8);
	public static final Currency BSD = crypto("BSD", 1e-8);
	public static final Currency BTB = crypto("BTB", 1e-8);
	public static final Currency BTCD = crypto("BTCD", 1e-8);
	public static final Currency BTG = crypto("BTG", 1e-8);
	public static final Currency BTQ = crypto("BTQ", 1e-8);
	public static final Currency BUK = crypto("BUK", 1e-8);
	public static final Currency BURST = crypto("BURST", 1e-8);
	public static final Currency BYC = crypto("BYC", 1e-8);
	public static final Currency C2 = crypto("C2", 1e-8);
	public static final Currency CANN = crypto("CANN", 1e-8);
	public static final Currency CDC = crypto("CDC", 1e-8);
	public static final Currency CENT = crypto("CENT", 1e-8);
	public static final Currency CFI = crypto("CFI", 1e-8);
	public static final Currency CLAM = crypto("CLAM", 1e-8);
	public static final Currency CLOAK = crypto("CLOAK", 1e-8);
	public static final Currency CLUB = crypto("CLUB", 1e-8);
	public static final Currency CMC = crypto("CMC", 1e-8);
	public static final Currency CNC = crypto("CNC", 1e-8);
	public static final Currency COMM = crypto("COMM", 1e-8);
	public static final Currency COVAL = crypto("COVAL", 1e-8);
	public static final Currency CPC = crypto("CPC", 1e-8);
	public static final Currency CRB = crypto("CRB", 1e-8);
	public static final Currency CRW = crypto("CRW", 1e-8);
	public static final Currency CURE = crypto("CURE", 1e-8);
	public static final Currency CVC = crypto("CVC", 1e-8);
	public static final Currency DASH = crypto("DASH", 1e-8);
	public static final Currency DCR = crypto("DCR", 1e-8);
	public static final Currency DCT = crypto("DCT", 1e-8);
	public static final Currency DGB = crypto("DGB", 1e-8);
	public static final Currency DGC = crypto("DGC", 1e-8);
	public static final Currency DGD = crypto("DGD", 1e-8);
	public static final Currency DMD = crypto("DMD", 1e-8);
	public static final Currency DNT = crypto("DNT", 1e-8);
	public static final Currency DOPE = crypto("DOPE", 1e-8);
	public static final Currency DTB = crypto("DTB", 1e-8);
	public static final Currency DTC = crypto("DTC", 1e-8);
	public static final Currency DVC = crypto("DVC", 1e-8);
	public static final Currency DYN = crypto("DYN", 1e-8);
	public static final Currency EBST = crypto("EBST", 1e-8);
	public static final Currency EDG = crypto("EDG", 1e-8);
	public static final Currency EFL = crypto("EFL", 1e-8);
	public static final Currency EGC = crypto("EGC", 1e-8);
	public static final Currency EMC = crypto("EMC", 1e-8);
	public static final Currency EMC2 = crypto("EMC2", 1e-8);
	public static final Currency ENG = crypto("ENG", 1e-8);
	public static final Currency ENRG = crypto("ENRG", 1e-8);
	public static final Currency EOS = crypto("EOS", 1e-8);
	public static final Currency ERC = crypto("ERC", 1e-8);
	public static final Currency ETH = crypto("ETH", 1e-8);
	public static final Currency EXC = crypto("EXC", 1e-8);
	public static final Currency EXCL = crypto("EXCL", 1e-8);
	public static final Currency EXP = crypto("EXP", 1e-8);
	public static final Currency FAIR = crypto("FAIR", 1e-8);
	public static final Currency FCT = crypto("FCT", 1e-8);
	public static final Currency FLDC = crypto("FLDC", 1e-8);
	public static final Currency FLO = crypto("FLO", 1e-8);
	public static final Currency FLT = crypto("FLT", 1e-8);
	public static final Currency FRC = crypto("FRC", 1e-8);
	public static final Currency FRST = crypto("FRST", 1e-8);
	public static final Currency FUN = crypto("FUN", 1e-8);
	public static final Currency GAM = crypto("GAM", 1e-8);
	public static final Currency GAME = crypto("GAME", 1e-8);
	public static final Currency GAS = crypto("GAS", 1e-8);
	public static final Currency GBG = crypto("GBG", 1e-8);
	public static final Currency GBYTE = crypto("GBYTE", 1e-8);
	public static final Currency GCR = crypto("GCR", 1e-8);
	public static final Currency GEO = crypto("GEO", 1e-8);
	public static final Currency GLD = crypto("GLD", 1e-8);
	public static final Currency GNO = crypto("GNO", 1e-8);
	public static final Currency GNT = crypto("GNT", 1e-8);
	public static final Currency GOLOS = crypto("GOLOS", 1e-8);
	public static final Currency GRC = crypto("GRC", 1e-8);
	public static final Currency GRS = crypto("GRS", 1e-8);
	public static final Currency GUP = crypto("GUP", 1e-8);
	public static final Currency HMQ = crypto("HMQ", 1e-8);
	public static final Currency ICN = crypto("ICN", 1e-8);
	public static final Currency IFC = crypto("IFC", 1e-8);
	public static final Currency INFX = crypto("INFX", 1e-8);
	public static final Currency IOC = crypto("IOC", 1e-8);
	public static final Currency ION = crypto("ION", 1e-8);
	public static final Currency IOP = crypto("IOP", 1e-8);
	public static final Currency IOT = crypto("IOT", 1e-8);
	public static final Currency KDC = crypto("KDC", 1e-8);
	public static final Currency KMD = crypto("KMD", 1e-8);
	public static final Currency KNC = crypto("KNC", 1e-8);
	public static final Currency KORE = crypto("KORE", 1e-8);
	public static final Currency LBC = crypto("LBC", 1e-8);
	public static final Currency LGD = crypto("LGD", 1e-8);
	public static final Currency LMC = crypto("LMC", 1e-8);
	public static final Currency LSK = crypto("LSK", 1e-8);
	public static final Currency LUN = crypto("LUN", 1e-8);
	public static final Currency MAID = crypto("MAID", 1e-8);
	public static final Currency MANA = crypto("MANA", 1e-8);
	public static final Currency MAX = crypto("MAX", 1e-8);
	public static final Currency MCO = crypto("MCO", 1e-8);
	public static final Currency MEC = crypto("MEC", 1e-8);
	public static final Currency MEME = crypto("MEME", 1e-8);
	public static final Currency MER = crypto("MER", 1e-8);
	public static final Currency MINT = crypto("MINT", 1e-8);
	public static final Currency MLN = crypto("MLN", 1e-8);
	public static final Currency MMC = crypto("MMC", 1e-8);
	public static final Currency MONA = crypto("MONA", 1e-8);
	public static final Currency MTL = crypto("MTL", 1e-8);
	public static final Currency MUE = crypto("MUE", 1e-8);
	public static final Currency MUSIC = crypto("MUSIC", 1e-8);
	public static final Currency MYST = crypto("MYST", 1e-8);
	public static final Currency NAV = crypto("NAV", 1e-8);
	public static final Currency NBT = crypto("NBT", 1e-8);
	public static final Currency NEC = crypto("NEC", 1e-8);
	public static final Currency NEOS = crypto("NEOS", 1e-8);
	public static final Currency NET = crypto("NET", 1e-8);
	public static final Currency NLG = crypto("NLG", 1e-8);
	public static final Currency NMR = crypto("NMR", 1e-8);
	public static final Currency NXC = crypto("NXC", 1e-8);
	public static final Currency NXS = crypto("NXS", 1e-8);
	public static final Currency OK = crypto("OK", 1e-8);
	public static final Currency OMG = crypto("OMG", 1e-8);
	public static final Currency OMNI = crypto("OMNI", 1e-8);
	public static final Currency PART = crypto("PART", 1e-8);
	public static final Currency PAY = crypto("PAY", 1e-8);
	public static final Currency PDC = crypto("PDC", 1e-8);
	public static final Currency PINK = crypto("PINK", 1e-8);
	public static final Currency PIVX = crypto("PIVX", 1e-8);
	public static final Currency PKB = crypto("PKB", 1e-8);
	public static final Currency POT = crypto("POT", 1e-8);
	public static final Currency POWR = crypto("POWR", 1e-8);
	public static final Currency PPC = crypto("PPC", 1e-8);
	public static final Currency PPT = crypto("PPT", 1e-8);
	public static final Currency PRT = crypto("PRT", 1e-8);
	public static final Currency PTC = crypto("PTC", 1e-8);
	public static final Currency PTOY = crypto("PTOY", 1e-8);
	public static final Currency PTS = crypto("PTS", 1e-8);
	public static final Currency QRK = crypto("QRK", 1e-8);
	public static final Currency QRL = crypto("QRL", 1e-8);
	public static final Currency QTUM = crypto("QTUM", 1e-8);
	public static final Currency quute = crypto("quute", 1e-8);
	public static final Currency RADS = crypto("RADS", 1e-8);
	public static final Currency RBY = crypto("RBY", 1e-8);
	public static final Currency RCN = crypto("RCN", 1e-8);
	public static final Currency RDD = crypto("RDD", 1e-8);
	public static final Currency RED = crypto("RED", 1e-8);
	public static final Currency REP = crypto("REP", 1e-8);
	public static final Currency RISE = crypto("RISE", 1e-8);
	public static final Currency RLC = crypto("RLC", 1e-8);
	public static final Currency RRT = crypto("RRT", 1e-8);
	public static final Currency SALT = crypto("SALT", 1e-8);
	public static final Currency SBD = crypto("SBD", 1e-8);
	public static final Currency SC = crypto("SC", 1e-8);
	public static final Currency SEQ = crypto("SEQ", 1e-8);
	public static final Currency SHIFT = crypto("SHIFT", 1e-8);
	public static final Currency SIB = crypto("SIB", 1e-8);
	public static final Currency SLM = crypto("SLM", 1e-8);
	public static final Currency SLR = crypto("SLR", 1e-8);
	public static final Currency SLS = crypto("SLS", 1e-8);
	public static final Currency SNRG = crypto("SNRG", 1e-8);
	public static final Currency SNT = crypto("SNT", 1e-8);
	public static final Currency SPHR = crypto("SPHR", 1e-8);
	public static final Currency SPR = crypto("SPR", 1e-8);
	public static final Currency SRC = crypto("SRC", 1e-8);
	public static final Currency START = crypto("START", 1e-8);
	public static final Currency STEEM = crypto("STEEM", 1e-8);
	public static final Currency STORJ = crypto("STORJ", 1e-8);
	public static final Currency STRAT = crypto("STRAT", 1e-8);
	public static final Currency SWIFT = crypto("SWIFT", 1e-8);
	public static final Currency SWT = crypto("SWT", 1e-8);
	public static final Currency SYNX = crypto("SYNX", 1e-8);
	public static final Currency SYS = crypto("SYS", 1e-8);
	public static final Currency TAG = crypto("TAG", 1e-8);
	public static final Currency THC = crypto("THC", 1e-8);
	public static final Currency TIPS = crypto("TIPS", 1e-8);
	public static final Currency TIX = crypto("TIX", 1e-8);
	public static final Currency TKS = crypto("TKS", 1e-8);
	public static final Currency TRIG = crypto("TRIG", 1e-8);
	public static final Currency TRST = crypto("TRST", 1e-8);
	public static final Currency TRUST = crypto("TRUST", 1e-8);
	public static final Currency TRX = crypto("TRX", 1e-8);
	public static final Currency TX = crypto("TX", 1e-8);
	public static final Currency UBQ = crypto("UBQ", 1e-8);
	public static final Currency UKG = crypto("UKG", 1e-8);
	public static final Currency UNB = crypto("UNB", 1e-8);
	public static final Currency USDT = crypto("USDT", 1e-8);
	public static final Currency VIA = crypto("VIA", 1e-8);
	public static final Currency VIB = crypto("VIB", 1e-8);
	public static final Currency VOX = crypto("VOX", 1e-8);
	public static final Currency VRC = crypto("VRC", 1e-8);
	public static final Currency VRM = crypto("VRM", 1e-8);
	public static final Currency VTC = crypto("VTC", 1e-8);
	public static final Currency VTR = crypto("VTR", 1e-8);
	public static final Currency WAVES = crypto("WAVES", 1e-8);
	public static final Currency WDC = crypto("WDC", 1e-8);
	public static final Currency WINGS = crypto("WINGS", 1e-8);
	public static final Currency XC = crypto("XC", 1e-8);
	public static final Currency XDN = crypto("XDN", 1e-8);
	public static final Currency XEL = crypto("XEL", 1e-8);
	public static final Currency XEM = crypto("XEM", 1e-8);
	public static final Currency XLM = crypto("XLM", 1e-8);
	public static final Currency XMG = crypto("XMG", 1e-8);
	public static final Currency XMR = crypto("XMR", 1e-8);
	public static final Currency XMY = crypto("XMY", 1e-8);
	public static final Currency XST = crypto("XST", 1e-8);
	public static final Currency XTZ = crypto("XTZ", 1e-8);
	public static final Currency XVC = crypto("XVC", 1e-8);
	public static final Currency XVG = crypto("XVG", 1e-8);
	public static final Currency XWC = crypto("XWC", 1e-8);
	public static final Currency XZC = crypto("XZC", 1e-8);
	public static final Currency YAC = crypto("YAC", 1e-8);
	public static final Currency ZCC = crypto("ZCC", 1e-8);
	public static final Currency ZCL = crypto("ZCL", 1e-8);
	public static final Currency ZEN = crypto("ZEN", 1e-8);
	public static final Currency ZET = crypto("ZET", 1e-8);
	public static final Currency ZOI = crypto("ZOI", 1e-8);
	public static final Currency ZRX = crypto("ZRX", 1e-8);

	/*
	 * these need currency basis research public static final Currency FORTY_TWO = crypto("42"); public static final Currency POINTS = crypto("Points");
	 * public static final Currency GDC = crypto("GDC"); public static final Currency CGB = crypto("CGB"); public static final Currency BTG =
	 * crypto("BTG"); public static final Currency BTE = crypto("BTE"); public static final Currency BTB = crypto("BTB"); public static final Currency
	 * FST = crypto("FST"); public static final Currency CASH = crypto("CASH"); public static final Currency IXC = crypto("IXC"); public static final
	 * Currency KGC = crypto("KGC"); public static final Currency XJO = crypto("XJO"); public static final Currency SPT = crypto("SPT"); public static
	 * final Currency LEAF = crypto("LEAF"); public static final Currency VTC = crypto("VTC"); public static final Currency LOT = crypto("LOT"); public
	 * static final Currency MEOW = crypto("MEOW"); public static final Currency BCX = crypto("BCX"); public static final Currency ARG = crypto("ARG");
	 * public static final Currency MNC = crypto("MNC"); public static final Currency COL = crypto("COL"); public static final Currency PXC =
	 * crypto("PXC"); public static final Currency ZCC = crypto("ZCC"); public static final Currency CLR = crypto("CLR"); public static final Currency
	 * TRC = crypto("TRC"); public static final Currency SBC = crypto("SBC"); public static final Currency ASC = crypto("ASC"); public static final
	 * Currency TIPS = crypto("TIPS"); public static final Currency ELP = crypto("ELP"); public static final Currency SRC = crypto("SRC"); public static
	 * final Currency IFC = crypto("IFC"); public static final Currency PYC = crypto("PYC"); public static final Currency CMC = crypto("CMC"); public
	 * static final Currency DMD = crypto("DMD"); public static final Currency TEK = crypto("TEK"); public static final Currency TIX = crypto("TIX");
	 * public static final Currency ALF = crypto("ALF"); public static final Currency PPC = crypto("PPC"); public static final Currency EMD =
	 * crypto("EMD"); public static final Currency RYC = crypto("RYC"); public static final Currency CACH = crypto("CACH"); public static final Currency
	 * NRB = crypto("NRB"); public static final Currency GME = crypto("GME"); public static final Currency BET = crypto("BET"); public static final
	 * Currency BUK = crypto("BUK"); public static final Currency GLX = crypto("GLX"); public static final Currency WDC = crypto("WDC"); public static
	 * final Currency DVC = crypto("DVC"); public static final Currency RPC = crypto("RPC"); public static final Currency CNC = crypto("CNC"); public
	 * static final Currency MEC = crypto("MEC"); public static final Currency FFC = crypto("FFC"); public static final Currency PHS = crypto("PHS");
	 * public static final Currency BEN = crypto("BEN"); public static final Currency FLO = crypto("FLO"); public static final Currency NAN =
	 * crypto("NAN"); public static final Currency ZET = crypto("ZET"); public static final Currency NBL = crypto("NBL"); public static final Currency
	 * OSC = crypto("OSC"); public static final Currency YBC = crypto("YBC"); public static final Currency QRK = crypto("QRK"); public static final
	 * Currency DEM = crypto("DEM"); public static final Currency LKY = crypto("LKY"); public static final Currency MIN = crypto("MIN"); public static
	 * final Currency EAC = crypto("EAC"); public static final Currency CSC = crypto("CSC"); public static final Currency ANC = crypto("ANC"); public
	 * static final Currency UTC = crypto("UTC"); public static final Currency CAT = crypto("CAT"); public static final Currency MST = crypto("MST");
	 * public static final Currency CAP = crypto("CAP"); public static final Currency STR = crypto("STR"); public static final Currency DGC =
	 * crypto("DGC"); public static final Currency SMC = crypto("SMC"); public static final Currency ADT = crypto("ADT"); public static final Currency
	 * LK7 = crypto("LK7"); public static final Currency ELC = crypto("ELC"); public static final Currency AMC = crypto("AMC"); public static final
	 * Currency MZC = crypto("MZC"); public static final Currency HBN = crypto("HBN"); public static final Currency EZC = crypto("EZC"); public static
	 * final Currency FRK = crypto("FRK"); public static final Currency FLAP = crypto("FLAP"); public static final Currency CPR = crypto("CPR"); public
	 * static final Currency SXC = crypto("SXC"); public static final Currency FRC = crypto("FRC"); public static final Currency TAK = crypto("TAK");
	 * public static final Currency BQC = crypto("BQC"); public static final Currency PTS = crypto("PTS"); public static final Currency RED =
	 * crypto("RED"); public static final Currency TAG = crypto("TAG"); public static final Currency UNO = crypto("UNO"); public static final Currency
	 * YAC = crypto("YAC"); public static final Currency NET = crypto("NET"); public static final Currency ORB = crypto("ORB"); public static final
	 * Currency DBL = crypto("DBL"); public static final Currency TGC = crypto("TGC"); public static final Currency XNC = crypto("XNC"); public static
	 * final Currency NEC = crypto("NEC"); public static final Currency CRC = crypto("CRC"); public static final Currency GLC = crypto("GLC"); public
	 * static final Currency GLD = crypto("GLD"); public static final Currency JKC = crypto("JKC"); public static final Currency MINT = crypto("MINT");
	 */

	private static Currency fiat(String symbol, double basis) {
		return Currency.forSymbolOrCreate(symbol, true, basis);
	}

	private static Currency crypto(String symbol, double basis) {
		return Currency.forSymbolOrCreate(symbol, false, basis);
	}
}
