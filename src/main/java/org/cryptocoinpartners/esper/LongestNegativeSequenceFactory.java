package org.cryptocoinpartners.esper;

import com.espertech.esper.client.hook.AggregationFunctionFactory;
import com.espertech.esper.epl.agg.aggregator.AggregationMethod;
import com.espertech.esper.epl.agg.service.AggregationValidationContext;

public class LongestNegativeSequenceFactory implements AggregationFunctionFactory {

	@Override
	public void setFunctionName(String functionName) {
		// TODO Auto-generated method stub

	}

	@Override
	public void validate(AggregationValidationContext validationContext) {
		if ((validationContext.getParameterTypes().length != 1) || (validationContext.getParameterTypes()[0] != Double.class)) {
			throw new IllegalArgumentException("LongestNegativeSequence aggregation requires a single parameter of type Double");
		}
	}

	@Override
	public AggregationMethod newAggregator() {
		return new LongestNegativeSequence();
	}

	@Override
	public Class getValueType() {
		// TODO Auto-generated method stub
		return Double.class;
	}
}
