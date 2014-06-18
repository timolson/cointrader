package org.cryptocoinpartners.command;

import com.google.inject.Injector;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.util.ReflectionUtil;
import org.reflections.Reflections;

import javax.inject.Inject;
import java.lang.reflect.Modifier;
import java.util.*;


/**
 * @author Tim Olson
 */
public abstract class CommandBase implements Command {

    public String getUsageHelp() { return null; }


    public void parse(String commandArguments) { }


    // these 2 are injected by ConsoleRunMode
    @Inject
    protected Context context;
    @Inject
    protected ConsoleWriter out;
    @Inject
    protected Injector injector;


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

            String[] commandNames;
            String className;
            CommandName commandNameAnn = commandClass.getAnnotation(CommandName.class);
            if( commandNameAnn != null ) {
                commandNames = commandNameAnn.value();
            }
            else {
                className = commandClass.getSimpleName();
                if( !className.endsWith("Command") ) {
                    throw new Error("If the name of your subclass of AntlrCommandBase doesn't end with \"Command\" then you need to use the AntlrCommandBase(String) constructor to pass in the name of your command");
                }
                commandNames = new String[]{className.substring(0,className.length() - "Command".length()).toLowerCase()};
            }

            for( String commandName : commandNames )
                commandClassesByName.put(commandName.toLowerCase(),commandClass);
        }
    }
}
