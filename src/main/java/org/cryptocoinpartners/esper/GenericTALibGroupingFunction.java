package org.cryptocoinpartners.esper;

import java.util.Arrays;
import java.util.HashMap;

import com.espertech.esper.epl.agg.service.AggregationSupport;
import com.espertech.esper.epl.agg.service.AggregationValidationContext;

/**
 * represents a clone of the GenericTALibFunction where grouing is used.
 * It uses the same function, outputClass, optInputParams and outputParams as the master
 *
 */
public class GenericTALibGroupingFunction extends AggregationSupport {
    HashMap<Object, GenericTALibFunction> groupedFunctions;

    public GenericTALibGroupingFunction() {
        super();

        groupedFunctions = new HashMap<Object, GenericTALibFunction>();
    }

    @Override
    public void enter(Object value) {
        // need to strip of first value as this is the key.
        int index = 0;
        Object key = null;

        Object[] values = (Object[]) value;
        for (Object item : values) {
            if (index == 0) {
                key = item;
                index++;
                continue;
            }
            values[index - 1] = item;
            if (index == values.length - 1)
                values[index] = null;
            index++;

        }
        // check if key is in hash map, if not adde it
        //    Arrays.copyOf(values, values.length-1);
        values = Arrays.copyOf(values, values.length - 1);
        if (groupedFunctions != null && groupedFunctions.isEmpty() || (key != null && groupedFunctions.get(key) == null)) {
            GenericTALibFunction taLib = new GenericTALibFunction();

            groupedFunctions.put(key, taLib);
            taLib.enter(values);

        } else
            groupedFunctions.get(key).enter(values);

    }

    @Override
    public void leave(Object value) {
        int index = 0;
        Object key = null;

        Object[] values = (Object[]) value;
        for (Object item : values) {
            if (index == 0) {
                key = item;
                index++;
                continue;
            }
            values[index - 1] = item;
            if (index == values.length - 1)
                values[index] = null;
            index++;

        }
        // check if key is in hash map, if not adde it
        //    Arrays.copyOf(values, values.length-1);
        values = Arrays.copyOf(values, values.length - 1);
        if (groupedFunctions != null && groupedFunctions.isEmpty() || (key != null && groupedFunctions.get(key) == null)) {
            GenericTALibFunction taLib = new GenericTALibFunction();

            groupedFunctions.put(key, taLib);
            taLib.leave(values);

        } else
            groupedFunctions.get(key).leave(values);

    }

    @Override
    public Object getValue() {
        HashMap<Object, Object> values = new HashMap<Object, Object>();

        for (Object key : groupedFunctions.keySet())
            values.put(key, groupedFunctions.get(key).getValue());

        return values;

    }

    @Override
    public Class getValueType() {
        for (Object key : groupedFunctions.keySet())
            return groupedFunctions.get(key).getValueType();
        return null;
    }

    @Override
    public void clear() {
        for (Object key : groupedFunctions.keySet())
            groupedFunctions.get(key).clear();

    }

    @Override
    public void validate(AggregationValidationContext validationContext) {
        // we take the first parma
        try {
            int index = 0;
            if (validationContext.getParameterTypes()[0] != String.class) {

                for (Class paramClass : validationContext.getParameterTypes()) {
                    if (index == 0) {
                        index++;
                        continue;
                    }
                    validationContext.getParameterTypes()[index - 1] = paramClass;
                    if (index == validationContext.getParameterTypes().length - 1)
                        validationContext.getParameterTypes()[index] = null;
                    index++;

                }

                index = 0;

                for (Object constant : validationContext.getConstantValues()) {
                    if (index == 0) {
                        index++;
                        continue;
                    }
                    validationContext.getConstantValues()[index - 1] = constant;
                    if (index == validationContext.getConstantValues().length - 1)
                        validationContext.getConstantValues()[index] = null;
                    index++;

                }
                index = 0;

                for (boolean isConstant : validationContext.getIsConstantValue()) {
                    if (index == 0) {
                        index++;
                        continue;
                    }
                    validationContext.getIsConstantValue()[index - 1] = isConstant;
                    if (index == validationContext.getIsConstantValue().length - 1)
                        validationContext.getIsConstantValue()[index] = false;
                    ;
                    index++;

                }

            }
            GenericTALibFunction taLib = new GenericTALibFunction();

            taLib.validate(validationContext);
            groupedFunctions.put(0, taLib);

            // create a hash
        } catch (Exception | Error ex) {
            throw ex;
        }

        // create and call vaildate on the child tail'bs

        // adde to hash map

    }

}
