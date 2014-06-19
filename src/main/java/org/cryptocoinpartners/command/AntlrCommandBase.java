package org.cryptocoinpartners.command;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provider;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.util.Config;
import org.cryptocoinpartners.util.Injector;
import org.cryptocoinpartners.util.ReflectionUtil;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;


/**
 * @author Tim Olson
 */
public abstract class AntlrCommandBase extends CommandBase {


    public abstract String getUsageHelp();


    public void parse( String commandArguments )
    {
        initCommandArgs();
        Lexer lexer = (Lexer) ReflectionUtil.instantiateClassByName(grammarPath + "Lexer",
                                                                    new Object[]{new ANTLRInputStream(commandArguments)},
                                                                    new Class[]{CharStream.class});
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Parser parser = (Parser) ReflectionUtil.instantiateClassByName(grammarPath + "Parser",
                                                                       new Object[]{tokens},
                                                                       new Class[]{TokenStream.class});
        initParseTreeListener();

        lexer.setInputStream(new ANTLRInputStream(commandArguments));
        Method argsMethod;
        try {
            argsMethod = parser.getClass().getMethod("args");
        }
        catch( NoSuchMethodException e ) {
            throw new Error("Your Antlr grammar must name the starting node \"args\"");
        }
        ParseTree tree;
        try {
            tree = (ParseTree) argsMethod.invoke(parser);
        }
        catch( InvocationTargetException | IllegalAccessException e ) {
            throw new Error("Could not invoke the args() method of "+parser.getClass().getName(),e);
        }
        catch( ClassCastException e ) {
            throw new Error("The args() method on "+parser.getClass().getName()+" must return an instance of type "+ParseTree.class.getName(),e);
        }
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, tree);
    }


    /**
     * this is called before each parse and should contain any initialization you need for your command options
     * before the ParseTreeWalker is invoked.
     */
    protected void initCommandArgs() {}


    @SuppressWarnings("UnusedDeclaration")
    protected AntlrCommandBase() {
        autoSetGrammarPath();
    }


    @SuppressWarnings("UnusedDeclaration")
    protected AntlrCommandBase(String grammarPath) {
        this.grammarPath = grammarPath;
    }


    private void autoSetGrammarPath() {
        Class<? extends AntlrCommandBase> cls = getClass();
        autoSetGrammarPath(cls);
    }


    private void autoSetGrammarPath(Class<? extends AntlrCommandBase> cls) {
        String name = cls.getSimpleName();
        if( !name.endsWith("Command") ) {
            throw new Error("If the name of your subclass of AntlrCommandBase doesn't end with \"Command\" then you need to use the AntlrCommandBase(Parser) constructor to pass in your command's Antlr Parser");
        }
        String baseClassName = name.substring(0,name.length() - "Command".length());
        grammarPath = cls.getPackage().getName() + "." + baseClassName;
    }


    @SuppressWarnings("unchecked")
    private void initParseTreeListener() {
        // to create the grammar's generated Listener we need to first find the Antlr generated class, then we look for
        // the cointrader subclass beneath the Antlr listener.
        String listenerClassName = grammarPath + "BaseListener";
        Class<? extends ParseTreeListener> listenerSubclass;
        Class listenerBaseClass = ReflectionUtil.classForName(listenerClassName);
        Set listenerSubtypes = ReflectionUtil.getCommandReflections().getSubTypesOf(listenerBaseClass);
        if( listenerSubtypes.size() > 1 )
            throw new Error("Found multiple subclasses of "+listenerClassName+":\n"+StringUtils.join(listenerSubtypes,',')+"\n.  Use the explicit AntlrCommandBase(String,Lexer,Parser,ParseTreeListener) constructor.");
        if( listenerSubtypes.isEmpty() )
            throw new Error("Could not find any subclass of "+listenerClassName+" in the command.path "+ Config.combined().getString("command.path"));
        listenerSubclass = (Class<? extends ParseTreeListener>) listenerSubtypes.iterator().next();
        Injector listenerInjector = injector.createChildInjector(new Module() {
            public void configure(Binder binder) {
                final Provider selfProvider = new SelfProvider();
                binder.bind(Command.class).toProvider(selfProvider);
                binder.bind(AntlrCommandBase.this.getClass()).toProvider(selfProvider);
            }
        });
        listenerInjector = getListenerInjector(listenerInjector);
        listener = listenerInjector.getInstance(listenerSubclass);
    }


    protected Injector getListenerInjector(Injector parentInjector) { return parentInjector; }


    protected void setListener(ParseTreeListener listener) { this.listener = listener; }


    private class SelfProvider implements Provider<AntlrCommandBase> {
        public AntlrCommandBase get() {
            return AntlrCommandBase.this;
        }
    }


    @Inject
    private Injector injector;
    private ParseTreeListener listener;
    private String grammarPath; // e.g. org.cryptocoinpartners.command.Order  Suffixes like "Parser" and "Lexer" will be appended
}
