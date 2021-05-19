package org.cryptocoinpartners.esper;

import com.espertech.esper.client.EventType;
import com.espertech.esper.core.context.util.AgentInstanceViewFactoryChainContext;
import com.espertech.esper.view.View;

public class HeikinAshiBarPlugInViewFactory extends OHLCBarPlugInViewFactory {

  @Override
  public String getViewName() {
    return HeikinAshiBarPlugInView.class.getSimpleName();
  }

  @Override
  public View makeView(AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext) {
    return new HeikinAshiBarPlugInView(
        agentInstanceViewFactoryContext,
        timestampExpression,
        valueExpression,
        volumeExpression,
        marketExpression,
        intervalExpression);
  }

  @Override
  public EventType getEventType() {
    return HeikinAshiBarPlugInView.getEventType(viewFactoryContext.getEventAdapterService());
  }
}
