package org.cryptocoinpartners.esper;

import java.util.HashMap;
import java.util.Iterator;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.EventType;
import com.espertech.esper.core.context.util.AgentInstanceViewFactoryChainContext;
import com.espertech.esper.epl.expression.ExprNode;
import com.espertech.esper.view.CloneableView;
import com.espertech.esper.view.View;
import com.espertech.esper.view.ViewSupport;

/**
 * Custom view to compute minute OHLC bars for double values and based on the event's timestamps.
 * <p>
 * Assumes events arrive in the order of timestamps, i.e. event 1 timestamp is always less or equal event 2 timestamp.
 * <p>
 * Implemented as a custom plug-in view rather then a series of EPL statements for the following reasons:
 *   - Custom output result mixing aggregation (min/max) and first/last values
 *   - No need for a data window retaining events if using a custom view
 *   - Unlimited number of groups (minute timestamps) makes the group-by clause hard to use
 */
public class GroupedOHLCBarPlugInView extends ViewSupport implements CloneableView {
    HashMap<Object, OHLCBarPlugInView> groupedOHLCPlugInView;

    public GroupedOHLCBarPlugInView(AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext, ExprNode timestampExpression, ExprNode valueExpression) {
    }

    public GroupedOHLCBarPlugInView(AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext, ExprNode timestampExpression,
            ExprNode valueExpression, ExprNode marketExpression, ExprNode intervalExpression) {
    }

    @Override
    public void update(EventBean[] newData, EventBean[] oldData) {
    }

    @Override
    public EventType getEventType() {
        return null;
    }

    @Override
    public Iterator<EventBean> iterator() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public View cloneView() {
        return null;
    }

}
