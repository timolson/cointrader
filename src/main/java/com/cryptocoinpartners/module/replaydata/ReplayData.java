package com.cryptocoinpartners.module.replaydata;

import com.clutch.dates.StringToTime;
import com.cryptocoinpartners.module.Esper;
import com.cryptocoinpartners.module.ModuleListenerBase;
import com.cryptocoinpartners.schema.Trade;
import com.cryptocoinpartners.util.Visitor;
import org.apache.commons.configuration.Configuration;
import org.joda.time.Interval;


public class ReplayData extends ModuleListenerBase
{
    public void initModule( final Esper esper, Configuration config )
    {
        super.initModule(esper, config);

    }
}
