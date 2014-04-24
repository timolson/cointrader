package com.cryptocoinpartners.bin.command;


import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.clutch.dates.StringToTime;
import com.cryptocoinpartners.module.Esper;
import com.cryptocoinpartners.util.Replay;
import org.joda.time.Instant;

import java.util.Date;
import java.util.List;


@Parameters( commandNames = "dump-ticks", commandDescription = "generate ticks into a csv file" )
public class DumpTicksCommand extends Command
{

    public void run()
    {
        // parse the start and end times
        Date start = null;
        if( startStr != null ) {
            try {
                start = new StringToTime(startStr);
            }
            catch( Exception e ) {
                log.error("Could not parse start time \"" + startStr + "\"");
                System.exit(7001);
            }
        }

        Date end = null;
        if( endStr != null ) {
            try {
                end = new StringToTime(endStr);
            }
            catch( Exception e ) {
                log.error("Could not parse end time \"" + endStr + "\"");
                System.exit(7001);
            }
        }


        Replay replay;
        if( start == null ) {
            if( end == null )
                replay = Replay.all();
            else
                replay = Replay.until(new Instant(end));
        }
        else if( end == null )
            replay = Replay.since(new Instant(start));
        else
            replay = Replay.between(new Instant(start), new Instant(end));


        Esper esper = replay.getEsper();
        esper.loadModule("tickwindow"); // generate ticks
        esper.loadModule("savetickscsv", "savetickscsv.filename", filenames.get(0)); // save ticks as csv
        replay.run();
        esper.destroy();
        System.exit(0);
    }


    @Parameter( names = { "-start" } )
    public String startStr = null;


    @Parameter( names = { "-end" } )
    public String endStr = null;


    @Parameter( required = true, arity = 1 )
    public List<String> filenames;
}
