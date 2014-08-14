package org.cryptocoinpartners.schema;

import java.util.List;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.util.PersistUtil;

/**
 * @author Tim Olson
 */
@Entity
public class Exchange extends EntityBase {

	public static Exchange forSymbolOrCreate(String symbol) {
		Exchange found = forSymbol(symbol);
		if (found == null) {
			found = new Exchange(symbol);
			PersistUtil.insert(found);
		}
		return found;
	}

	public static Exchange forSymbolOrCreate(String symbol, double margin, double feeRate, FeeMethod feeMethod) {
		Exchange found = forSymbol(symbol);
		if (found == null) {
			found = new Exchange(symbol, margin, feeRate, feeMethod);
			PersistUtil.insert(found);
		}
		return found;
	}

	public static Exchange forSymbolOrCreate(String symbol, double margin, double feeRate, FeeMethod feeMethod, double marginFeeRate, FeeMethod marginFeeMethod) {
		Exchange found = forSymbol(symbol);
		if (found == null) {
			found = new Exchange(symbol, margin, feeRate, feeMethod, marginFeeRate, marginFeeMethod);
			PersistUtil.insert(found);
		}
		return found;
	}

	/** returns null if the symbol does not represent an existing exchange */
	public static Exchange forSymbol(String symbol) {
		return PersistUtil.queryZeroOne(Exchange.class, "select e from Exchange e where symbol=?1", symbol);
	}

	public static List<String> allSymbols() {
		return PersistUtil.queryList(String.class, "select symbol from Exchange");
	}

	@Basic(optional = false)
	public String getSymbol() {
		return symbol;
	}

	@Basic(optional = false)
	public double getFeeRate() {
		return feeRate;
	}

	protected void setFeeRate(double feeRate) {
		this.feeRate = feeRate;
	}

	@Basic(optional = true)
	public double getMarginFeeRate() {
		return marginFeeRate;
	}

	protected void setMarginFeeRate(double marginFeeRate) {
		this.marginFeeRate = marginFeeRate;
	}

	@ManyToOne(optional = false)
	private FeeMethod feeMethod;

	public FeeMethod getFeeMethod() {
		return feeMethod;
	}

	protected void setFeeMethod(FeeMethod feeMethod) {
		this.feeMethod = feeMethod;
	}

	@ManyToOne(optional = true)
	private FeeMethod marginFeeMethod;

	public FeeMethod getMarginFeeMethod() {
		return marginFeeMethod;
	}

	protected void setMarginFeeMethod(FeeMethod marginFeeMethod) {
		this.marginFeeMethod = marginFeeMethod;
	}

	@Basic(optional = false)
	public double getMargin() {
		return margin;
	}

	protected void setMargin(double margin) {
		this.margin = margin;
	}

	@Override
	public String toString() {
		return symbol;
	}

	// JPA
	protected Exchange() {
	}

	protected void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	private Exchange(String symbol, double margin, double feeRate, FeeMethod feeMethod) {
		this.symbol = symbol;
		this.margin = margin;
		this.feeRate = feeRate;
		this.feeMethod = feeMethod;
	}

	private Exchange(String symbol, double margin, double feeRate, FeeMethod feeMethod, double marginFeeRate, FeeMethod marginFeeMethod) {
		this.symbol = symbol;
		this.margin = margin;
		this.feeRate = feeRate;
		this.feeMethod = feeMethod;
		this.marginFeeMethod = marginFeeMethod;
		this.marginFeeRate = marginFeeRate;
	}

	private Exchange(String symbol) {
		this.symbol = symbol;

	}

	private String symbol;
	private double margin;
	private double feeRate;
	private double marginFeeRate;

}
