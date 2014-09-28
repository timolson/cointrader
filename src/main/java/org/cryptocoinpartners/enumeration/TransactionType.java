package org.cryptocoinpartners.enumeration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Transient;

/**
 * 
 */
public enum TransactionType {
	BUY("BUY"), //0
	SELL("SELL"), //1
	BUY_RESERVATION("BUY_RESERVATION"), //2
	SELL_RESERVATION("SELL_RESERVATION"), //3
	CREDIT("CREDIT"), //4
	DEBIT("DEBIT"), //5
	INTREST("INTREST"), //6
	FEES("FEES"), //7
	REBALANCE("REBALANCE"), //8
	TRANSFER("TRANSFER"), //9
	PURCHASE("PURCHASE");//10

	private final String enumValue;

	private TransactionType(String value) {
		this.enumValue = value;
	}

	@Transient
	public static TransactionType fromString(String name) {
		return TransactionType.valueOf(name);
	}

	@Transient
	public String value() {
		return this.enumValue;
	}

	public static TransactionType fromValue(String value) {
		for (TransactionType enumName : TransactionType.values()) {
			if (enumName.getValue().equals(value)) {
				return enumName;
			}
		}
		throw new IllegalArgumentException("TransactionType.fromValue(" + value + ')');
	}

	@Transient
	public String getValue() {
		return this.enumValue;
	}

	@Transient
	public static List<String> literals() {
		return TransactionType.literals;
	}

	@Transient
	public static List<String> names() {
		return TransactionType.names;
	}

	private static Map<String, TransactionType> values = new LinkedHashMap<>(9, 1);
	private static List<String> literals = new ArrayList<>(9);
	private static List<String> names = new ArrayList<>(9);
	private static List<TransactionType> valueList = new ArrayList<>(9);

	/**
	 * Initializes the values.
	 */
	static {
		synchronized (TransactionType.values) {
			TransactionType.values.put(BUY.enumValue, BUY); //0
			TransactionType.values.put(SELL.enumValue, SELL); //1
			TransactionType.values.put(BUY.enumValue, BUY_RESERVATION); //2
			TransactionType.values.put(SELL_RESERVATION.enumValue, SELL_RESERVATION); //3
			TransactionType.values.put(CREDIT.enumValue, CREDIT); //4
			TransactionType.values.put(DEBIT.enumValue, DEBIT); //5
			TransactionType.values.put(INTREST.enumValue, INTREST);//6
			TransactionType.values.put(FEES.enumValue, FEES); //7
			TransactionType.values.put(REBALANCE.enumValue, REBALANCE); //8
			TransactionType.values.put(TRANSFER.enumValue, TRANSFER); //9
			TransactionType.values.put(PURCHASE.enumValue, PURCHASE); //10
		}
		synchronized (TransactionType.valueList) {
			TransactionType.valueList.add(BUY);
			TransactionType.valueList.add(SELL);
			TransactionType.valueList.add(BUY_RESERVATION);
			TransactionType.valueList.add(SELL_RESERVATION);
			TransactionType.valueList.add(CREDIT);
			TransactionType.valueList.add(DEBIT);
			TransactionType.valueList.add(INTREST);
			TransactionType.valueList.add(FEES);
			TransactionType.valueList.add(REBALANCE);
			TransactionType.valueList.add(TRANSFER);
			TransactionType.valueList.add(PURCHASE);
			TransactionType.valueList = Collections.unmodifiableList(valueList);
		}
		synchronized (TransactionType.literals) {
			TransactionType.literals.add(BUY.enumValue);
			TransactionType.literals.add(SELL.enumValue);
			TransactionType.literals.add(BUY_RESERVATION.enumValue);
			TransactionType.literals.add(SELL_RESERVATION.enumValue);
			TransactionType.literals.add(CREDIT.enumValue);
			TransactionType.literals.add(DEBIT.enumValue);
			TransactionType.literals.add(INTREST.enumValue);
			TransactionType.literals.add(FEES.enumValue);
			TransactionType.literals.add(REBALANCE.enumValue);
			TransactionType.literals.add(TRANSFER.enumValue);
			TransactionType.literals.add(PURCHASE.enumValue);
			TransactionType.literals = Collections.unmodifiableList(literals);
		}
		synchronized (TransactionType.names) {
			TransactionType.names.add("BUY");
			TransactionType.names.add("SELL");
			TransactionType.names.add("BUY_RESERVATION");
			TransactionType.names.add("SELL_RESERVATION");
			TransactionType.names.add("CREDIT");
			TransactionType.names.add("DEBIT");
			TransactionType.names.add("INTREST");
			TransactionType.names.add("FEES");
			TransactionType.names.add("REBALANCE");
			TransactionType.names.add("TRANSFER");
			TransactionType.names.add("PURCHASE");
			TransactionType.names = Collections.unmodifiableList(names);
		}
	}

}
