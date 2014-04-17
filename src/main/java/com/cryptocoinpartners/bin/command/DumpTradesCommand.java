package com.cryptocoinpartners.bin.command;


import com.beust.jcommander.Parameters;


@Parameters(commandNames = "dump-ticks", commandDescription = "generate ticks into a csv file")
public class DumpTradesCommand extends Command
{
    public void run()
    {
        String[] headers = new String[] {"listing","base","quote","time","price"};
    }
}
