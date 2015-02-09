package org.cryptocoinpartners.esper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections15.buffer.CircularFifoBuffer;

/**
 * represents a clone of the GenericTALibFunction where aggregation is used.
 * It uses the same function, outputClass, optInputParams and outputParams as the master
 *
 */
public class GenericTALibAggregatorFunction extends GenericTALibFunction {

    public GenericTALibAggregatorFunction(Method function, int inputParamCount, int lookbackPeriod, List<Object> optInputParams,
            Map<String, Object> outputParams, Class<?> outputClass) {

        super();

        this.function = function;
        this.outputClass = outputClass;

        this.optInputParams = optInputParams;
        this.outputParams = outputParams;

        this.inputParams = new ArrayList<>();

        for (int i = 0; i < inputParamCount; i++) {
            this.inputParams.add(new CircularFifoBuffer<Number>(lookbackPeriod));
        }
    }
}
