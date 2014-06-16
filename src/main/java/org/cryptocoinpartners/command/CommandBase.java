package org.cryptocoinpartners.command;

import com.google.inject.Injector;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.util.ReflectionUtil;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.*;


/**
 * @author Tim Olson
 */
public abstract class CommandBase implements Command {


    public String getCommandName() {
        return commandName;
    }

    public void parse(String commandArguments) { }

    protected void setCommandName(String commandName) {
        this.commandName = commandName;
    }

    @SuppressWarnings("UnusedDeclaration")
    protected CommandBase(String commandName) {
        setCommandName(commandName);
    }


    /** This constructor will infer the command name from the naming of your subclass: e.g. BuyCommand has a commandName
     *  of "buy" */
    protected CommandBase() {
        String name = getClass().getSimpleName();
        if( !name.endsWith("Command") ) {
            throw new Error("If the name of your subclass of AntlrCommandBase doesn't end with \"Command\" then you need to use the AntlrCommandBase(String) constructor to pass in the name of your command");
        }
        setCommandName(name.substring(0,name.length() - "Command".length()).toLowerCase());
    }


    // these 2 are injected by ConsoleRunMode
    public Context context;
    public ConsoleWriter out;

    private String commandName;


    public static List<String> allCommandNames() {
        ArrayList<String> names = new ArrayList<>(commandClassesByName.keySet());
        Collections.sort(names);
        return names;
    }


    public static Command commandForName( String name, Context context ) {
        Class<? extends Command> commandClass = commandClassesByName.get(name.toLowerCase());
        if( commandClass == null )
            return null;
        return context.attach(commandClass);
    }


    private static Map<String,Class<? extends Command>> commandClassesByName;


    static {
        commandClassesByName = new HashMap<>();
        Reflections reflections = ReflectionUtil.getCommandReflections();
        Set<Class<? extends Command>> commandClasses = reflections.getSubTypesOf(Command.class);
        for( Class<? extends Command> commandClass : commandClasses ) {
            int modifiers = commandClass.getModifiers();
            if( Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers) )
                continue;
            try {
                commandClassesByName.put(commandClass.newInstance().getCommandName().toLowerCase(),commandClass);
            }
            catch( InstantiationException | IllegalAccessException e ) {
                throw new Error("Could not instantiate command "+commandClass,e);
            }
        }
    }


}
