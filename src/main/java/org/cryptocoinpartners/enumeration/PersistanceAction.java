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
public enum PersistanceAction {
    NEW("NEW"), MERGE("MERGE"), DELETE("DELETE");

    private final String enumValue;

    private PersistanceAction(String value) {
        this.enumValue = value;
    }

    @Transient
    public static PersistanceAction fromString(String name) {
        return PersistanceAction.valueOf(name);
    }

    @Transient
    public String value() {
        return this.enumValue;
    }

    public static PersistanceAction fromValue(String value) {
        for (PersistanceAction enumName : PersistanceAction.values()) {
            if (enumName.getValue().equals(value)) {
                return enumName;
            }
        }
        throw new IllegalArgumentException("PersistanceAction.fromValue(" + value + ')');
    }

    @Transient
    public String getValue() {
        return this.enumValue;
    }

    @Transient
    public static List<String> literals() {
        return PersistanceAction.literals;
    }

    @Transient
    public static List<String> names() {
        return PersistanceAction.names;
    }

    private static Map<String, PersistanceAction> values = new LinkedHashMap<>(9, 1);
    private static List<String> literals = new ArrayList<>(9);
    private static List<String> names = new ArrayList<>(9);
    private static List<PersistanceAction> valueList = new ArrayList<>(9);

    /**
     * Initializes the values.
     */
    static {
        synchronized (PersistanceAction.values) {
            PersistanceAction.values.put(NEW.enumValue, NEW);
            PersistanceAction.values.put(MERGE.enumValue, MERGE);
            PersistanceAction.values.put(DELETE.enumValue, DELETE);

        }
        synchronized (PersistanceAction.valueList) {
            PersistanceAction.valueList.add(NEW);
            PersistanceAction.valueList.add(MERGE);
            PersistanceAction.valueList.add(DELETE);

            PersistanceAction.valueList = Collections.unmodifiableList(valueList);
        }
        synchronized (PersistanceAction.literals) {
            PersistanceAction.literals.add(NEW.enumValue);
            PersistanceAction.literals.add(MERGE.enumValue);
            PersistanceAction.literals.add(DELETE.enumValue);

            PersistanceAction.literals = Collections.unmodifiableList(literals);
        }
        synchronized (PersistanceAction.names) {
            PersistanceAction.names.add("NEW");
            PersistanceAction.names.add("MERGE");
            PersistanceAction.names.add("DELETE");
            PersistanceAction.names = Collections.unmodifiableList(names);
        }
    }

}
