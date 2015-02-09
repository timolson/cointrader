package org.cryptocoinpartners.esper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;

import org.apache.commons.collections15.buffer.CircularFifoBuffer;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.StringUtils;

import com.espertech.esper.epl.agg.aggregator.AggregationMethod;
import com.espertech.esper.epl.agg.service.AggregationSupport;
import com.espertech.esper.epl.agg.service.AggregationValidationContext;
import com.espertech.esper.epl.core.MethodResolutionService;
import com.espertech.esper.epl.expression.ExprEvaluator;
import com.tictactec.ta.lib.CoreAnnotated;
import com.tictactec.ta.lib.MAType;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;
import com.tictactec.ta.lib.meta.annotation.InputParameterInfo;
import com.tictactec.ta.lib.meta.annotation.InputParameterType;
import com.tictactec.ta.lib.meta.annotation.OptInputParameterInfo;
import com.tictactec.ta.lib.meta.annotation.OptInputParameterType;
import com.tictactec.ta.lib.meta.annotation.OutputParameterInfo;
import com.tictactec.ta.lib.meta.annotation.OutputParameterType;

/*
 * Talib libary example usage select talib("movingAverage", askPriceCountAsDouble, 3, "Sma") - talib("movingAverage", askPriceCountAsDouble, 5, "Sma") as value
 * from Book;
 */

public class GenericTALibFunction extends AggregationSupport {

    static CoreAnnotated core = new CoreAnnotated();

    Method function;
    Class<?> outputClass;

    int inputParamCount;
    int lookbackPeriod;
    boolean init = false;

    List<CircularFifoBuffer<Number>> inputParams;
    List<Object> optInputParams;
    Map<String, Object> outputParams;

    public GenericTALibFunction() {

        super();
        this.inputParamCount = 0;
        this.inputParams = new ArrayList<>();
        this.optInputParams = new ArrayList<>();
        this.outputParams = new HashMap<>();
    }

    @Override
    public void validate(AggregationValidationContext validationContext) {

        Class<?>[] paramTypes = validationContext.getParameterTypes();

        // get the functionname
        String talibFunctionName = (String) getConstant(validationContext, 0, String.class);

        // get the method by iterating over all core-methods
        // we have to do it this way, since we don't have the exact parameters
        for (Method method : core.getClass().getDeclaredMethods()) {
            if (method.getName().equals(talibFunctionName)) {
                this.function = method;
                break;
            }
        }

        // check that we have a function now
        if (this.function == null) {
            throw new IllegalArgumentException("function " + talibFunctionName + " was not found");
        }

        // get the parameters
        int paramCounter = 1;
        Map<String, Class<?>> outputParamTypes = new HashMap<>();
        for (Annotation[] annotations : this.function.getParameterAnnotations()) {
            for (Annotation annotation : annotations) {

                // got through all inputParameters and count them
                if (annotation instanceof InputParameterInfo) {
                    InputParameterInfo inputParameterInfo = (InputParameterInfo) annotation;
                    if (inputParameterInfo.type().equals(InputParameterType.TA_Input_Real)) {
                        if (paramTypes[paramCounter] == null) {
                            return;
                        }

                        else if (paramTypes[paramCounter].equals(double.class) || paramTypes[paramCounter].equals(Double.class)) {
                            this.inputParamCount++;
                            paramCounter++;
                        } else {
                            throw new IllegalArgumentException("param number " + paramCounter + " needs must be of type double");
                        }
                    } else if (inputParameterInfo.type().equals(InputParameterType.TA_Input_Integer)) {
                        if (paramTypes[paramCounter].equals(int.class) || paramTypes[paramCounter].equals(Integer.class)) {
                            this.inputParamCount++;
                            paramCounter++;
                        } else {
                            throw new IllegalArgumentException("param number " + paramCounter + " needs must be of type int");
                        }
                    } else if (inputParameterInfo.type().equals(InputParameterType.TA_Input_Price)) {

                        // the flags define the number of parameters in use by a bitwise or
                        int priceParamSize = numberOfSetBits(inputParameterInfo.flags());
                        for (int i = 0; i < priceParamSize; i++) {
                            if (paramTypes[paramCounter].equals(double.class) || paramTypes[paramCounter].equals(Double.class)) {
                                this.inputParamCount++;
                                paramCounter++;
                            } else {
                                throw new IllegalArgumentException("param number " + paramCounter + " needs must be of type double");
                            }
                        }
                    }

                    // got through all optInputParameters and store them for later
                } else if (annotation instanceof OptInputParameterInfo) {
                    OptInputParameterInfo optInputParameterInfo = (OptInputParameterInfo) annotation;
                    if (optInputParameterInfo.type().equals(OptInputParameterType.TA_OptInput_IntegerRange)) {
                        if (validationContext.getConstantValues()[paramCounter] == null) {
                            return;
                        } else {

                            this.optInputParams.add(getConstant(validationContext, paramCounter, Integer.class));
                        }
                    } else if (optInputParameterInfo.type().equals(OptInputParameterType.TA_OptInput_RealRange)) {
                        this.optInputParams.add(getConstant(validationContext, paramCounter, Double.class));
                    } else if (optInputParameterInfo.type().equals(OptInputParameterType.TA_OptInput_IntegerList)) {
                        String value = (String) getConstant(validationContext, paramCounter, String.class);
                        MAType type = MAType.valueOf(value);
                        this.optInputParams.add(type);
                    }
                    paramCounter++;

                    // to through all outputParameters and store them
                } else if (annotation instanceof OutputParameterInfo) {
                    OutputParameterInfo outputParameterInfo = (OutputParameterInfo) annotation;
                    String paramName = outputParameterInfo.paramName();
                    if (outputParameterInfo.type().equals(OutputParameterType.TA_Output_Real)) {
                        this.outputParams.put(paramName, new double[1]);
                        outputParamTypes.put(paramName.toLowerCase().substring(3), double.class);
                    } else if (outputParameterInfo.type().equals(OutputParameterType.TA_Output_Integer)) {
                        this.outputParams.put(outputParameterInfo.paramName(), new int[1]);
                        outputParamTypes.put(paramName.toLowerCase().substring(3), int.class);
                    }
                }
            }
        }

        try {

            // get the dynamically created output class
            if (this.outputParams.size() > 1) {
                String className = StringUtils.capitalize(talibFunctionName);
                this.outputClass = getReturnClass(className, outputParamTypes);
            }

            // get the lookback size
            Object[] args = new Object[this.optInputParams.size()];
            Class<?>[] argTypes = new Class[this.optInputParams.size()];

            // supply all optInputParams
            int argCount = 0;
            for (Object object : this.optInputParams) {
                args[argCount] = object;
                Class<?> clazz = object.getClass();
                Class<?> primitiveClass = ClassUtils.wrapperToPrimitive(clazz);
                if (primitiveClass != null) {
                    argTypes[argCount] = primitiveClass;
                } else {
                    argTypes[argCount] = clazz;
                }
                argCount++;
            }

            // get and invoke the lookback method
            Method lookback = core.getClass().getMethod(talibFunctionName + "Lookback", argTypes);
            this.lookbackPeriod = (Integer) lookback.invoke(core, args) + 1;

            // create the fixed size Buffers
            for (int i = 0; i < this.inputParamCount; i++) {
                this.inputParams.add(new CircularFifoBuffer<Number>(this.lookbackPeriod));
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void enter(Object obj) {

        Object[] params = (Object[]) obj;

        // get the functionname
        if (!init) {
            String talibFunctionName = (String) params[0];
            for (Method method : core.getClass().getDeclaredMethods()) {
                if (method.getName().equals(talibFunctionName)) {
                    this.function = method;
                    break;
                }
            }
            // get the parameters
            int paramCounter = 1;
            Map<String, Class<?>> outputParamTypes = new HashMap<>();
            for (Annotation[] annotations : this.function.getParameterAnnotations()) {
                for (Annotation annotation : annotations) {
                    if (annotation instanceof InputParameterInfo) {
                        InputParameterInfo inputParameterInfo = (InputParameterInfo) annotation;
                        if (inputParameterInfo.type().equals(InputParameterType.TA_Input_Real)) {
                            this.inputParamCount++;
                            paramCounter++;
                        } else if (inputParameterInfo.type().equals(InputParameterType.TA_Input_Integer)) {
                            this.inputParamCount++;
                            paramCounter++;

                        } else if (inputParameterInfo.type().equals(InputParameterType.TA_Input_Price)) {

                            int priceParamSize = numberOfSetBits(inputParameterInfo.flags());
                            for (int i = 0; i < priceParamSize; i++) {
                                this.inputParamCount++;
                                paramCounter++;
                            }
                        }
                    }

                    else if (annotation instanceof OptInputParameterInfo) {
                        OptInputParameterInfo optInputParameterInfo = (OptInputParameterInfo) annotation;
                        if (optInputParameterInfo.type().equals(OptInputParameterType.TA_OptInput_IntegerRange)) {
                            this.optInputParams.add((params[paramCounter]));
                        } else if (optInputParameterInfo.type().equals(OptInputParameterType.TA_OptInput_RealRange)) {
                            this.optInputParams.add(params[paramCounter]);
                        } else if (optInputParameterInfo.type().equals(OptInputParameterType.TA_OptInput_IntegerList)) {
                            String value = (String) params[paramCounter];
                            MAType type = MAType.valueOf(value);
                            this.optInputParams.add(type);
                        }
                        paramCounter++;

                        // to through all outputParameters and store them
                    } else if (annotation instanceof OutputParameterInfo) {
                        OutputParameterInfo outputParameterInfo = (OutputParameterInfo) annotation;
                        String paramName = outputParameterInfo.paramName();
                        if (outputParameterInfo.type().equals(OutputParameterType.TA_Output_Real)) {
                            this.outputParams.put(paramName, new double[1]);
                            outputParamTypes.put(paramName.toLowerCase().substring(3), double.class);
                        } else if (outputParameterInfo.type().equals(OutputParameterType.TA_Output_Integer)) {
                            this.outputParams.put(outputParameterInfo.paramName(), new int[1]);
                            outputParamTypes.put(paramName.toLowerCase().substring(3), int.class);
                        }
                    }
                }
            }

            try {

                // get the dynamically created output class
                if (this.outputParams.size() > 1) {
                    String className = StringUtils.capitalize(talibFunctionName);
                    this.outputClass = getReturnClass(className, outputParamTypes);
                }

                // get the lookback size
                Object[] args = new Object[this.optInputParams.size()];
                Class<?>[] argTypes = new Class[this.optInputParams.size()];

                // supply all optInputParams
                int argCount = 0;
                for (Object object : this.optInputParams) {
                    args[argCount] = object;
                    Class<?> clazz = object.getClass();
                    Class<?> primitiveClass = ClassUtils.wrapperToPrimitive(clazz);
                    if (primitiveClass != null) {
                        argTypes[argCount] = primitiveClass;
                    } else {
                        argTypes[argCount] = clazz;
                    }
                    argCount++;
                }

                // get and invoke the lookback method
                Method lookback = core.getClass().getMethod(talibFunctionName + "Lookback", argTypes);
                this.lookbackPeriod = (Integer) lookback.invoke(core, args) + 1;

                // create the fixed size Buffers
                for (int i = 0; i < this.inputParamCount; i++) {
                    this.inputParams.add(new CircularFifoBuffer<Number>(this.lookbackPeriod));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            init = true;
        }
        // got through all inputParameters and count them

        //this.inputParams.add(new CircularFifoBuffer<Number>(3));

        // add all inputs to the correct buffers
        int paramCount = 1;
        for (CircularFifoBuffer<Number> buffer : this.inputParams) {
            Number value = (Number) params[paramCount];
            buffer.add(value);
            paramCount++;
        }
    }

    @Override
    public void leave(Object obj) {
        // Remove the last element of each buffer
        //init = false;
        for (CircularFifoBuffer<Number> buffer : this.inputParams) {
            if (buffer.contains(obj)) {
                buffer.remove(obj);
            }
        }
    }

    @Override
    public Class<?> getValueType() {

        // if we only have one outPutParam return that value
        // otherwise return the dynamically generated class
        if (this.outputParams.size() == 1) {
            Class<?> clazz = this.outputParams.values().iterator().next().getClass();
            if (clazz.isArray()) {
                return clazz.getComponentType();
            } else {
                return clazz;
            }
        } else {
            return this.outputClass;
        }
    }

    @Override
    public Object getValue() {

        try {
            // get the total number of parameters
            int numberOfArgs = 2 + this.inputParams.size() + this.optInputParams.size() + 2 + this.outputParams.size();
            Object[] args = new Object[numberOfArgs];

            // get the size of the first input buffer
            int elements = this.inputParams.iterator().next().size();

            args[0] = elements - 1; // startIdx
            args[1] = elements - 1; // endIdx

            // inputParams
            int argCount = 2;
            for (CircularFifoBuffer<Number> buffer : this.inputParams) {

                // look at the first element of the buffer to determine the type
                Object firstElement = buffer.iterator().next();
                if (firstElement instanceof Double) {
                    args[argCount] = ArrayUtils.toPrimitive(buffer.toArray(new Double[0]));
                } else if (firstElement instanceof Integer) {
                    args[argCount] = ArrayUtils.toPrimitive(buffer.toArray(new Integer[0]));
                } else {
                    throw new IllegalArgumentException("unsupported type " + firstElement.getClass());
                }
                argCount++;
            }

            // optInputParams
            for (Object object : this.optInputParams) {
                args[argCount] = object;
                argCount++;
            }

            // begin
            MInteger begin = new MInteger();
            args[argCount] = begin;
            argCount++;

            // length
            MInteger length = new MInteger();
            args[argCount] = length;
            argCount++;

            // OutputParams
            for (Map.Entry<String, Object> entry : this.outputParams.entrySet()) {
                args[argCount++] = entry.getValue();
            }

            // invoke the function
            RetCode retCode = (RetCode) this.function.invoke(core, args);

            if (retCode == RetCode.Success) {
                if (length.value == 0) {
                    return null;
                }

                // if we only have one outPutParam return that value
                // otherwise return a Map
                if (this.outputParams.size() == 1) {
                    Object value = this.outputParams.values().iterator().next();
                    return getNumberFromNumberArray(value);
                } else {
                    Object returnObject = this.outputClass.newInstance();
                    for (Map.Entry<String, Object> entry : this.outputParams.entrySet()) {
                        Number value = getNumberFromNumberArray(entry.getValue());
                        String name = entry.getKey().toLowerCase().substring(3);

                        Field field = this.outputClass.getField(name);
                        field.set(returnObject, value);
                    }
                    return returnObject;
                }
            } else {
                throw new RuntimeException(retCode.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clear() {

        // clear all elements from the buffers
        //  init = false;
        for (CircularFifoBuffer<Number> buffer : this.inputParams) {
            buffer.clear();
        }
    }

    //@Override
    public AggregationMethod newAggregator(MethodResolutionService methodResolutionService) {

        return new GenericTALibAggregatorFunction(this.function, this.inputParamCount, this.lookbackPeriod, this.optInputParams, this.outputParams,
                this.outputClass);
    }

    private Number getNumberFromNumberArray(Object value) {

        if (value instanceof double[]) {
            return ((double[]) value)[0];
        } else if (value instanceof int[]) {
            return ((int[]) value)[0];
        } else {
            throw new IllegalArgumentException(value.getClass() + " not supported");
        }
    }

    private int numberOfSetBits(int i) {
        i = i - ((i >> 1) & 0x55555555);
        i = (i & 0x33333333) + ((i >> 2) & 0x33333333);
        return ((i + (i >> 4) & 0xF0F0F0F) * 0x1010101) >> 24;
    }

    private Class<?> getReturnClass(String className, Map<String, Class<?>> fields) throws CannotCompileException, NotFoundException {

        String fqClassName = this.getClass().getPackage().getName() + "." + className;

        try {
            // see if the class already exists
            return Class.forName(fqClassName);

        } catch (ClassNotFoundException e) {

            // otherwise create the class
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.makeClass(fqClassName);

            for (Map.Entry<String, Class<?>> entry : fields.entrySet()) {

                // generate a public field (we don't need a setter)
                String fieldName = entry.getKey();
                CtClass valueClass = pool.get(entry.getValue().getName());
                CtField ctField = new CtField(valueClass, fieldName, ctClass);
                ctField.setModifiers(Modifier.PUBLIC);
                ctClass.addField(ctField);

                // generate the getter method
                String methodName = "get" + StringUtils.capitalize(fieldName);
                CtMethod ctMethod = CtNewMethod.make(valueClass, methodName, new CtClass[] {}, new CtClass[] {}, "{ return this." + fieldName + ";}", ctClass);
                ctClass.addMethod(ctMethod);
            }
            return ctClass.toClass();
        }
    }

    private Object getConstant(AggregationValidationContext validationContext, int index, Class<?> clazz) {

        if (index >= validationContext.getIsConstantValue().length) {
            throw new IllegalArgumentException("only " + validationContext.getIsConstantValue().length + " params have been specified, should be "
                    + (index + 1));
        }

        if (validationContext.getIsConstantValue()[index]) {
            if (validationContext.getParameterTypes()[index].equals(clazz)) {
                return validationContext.getConstantValues()[index];
            } else {
                throw new IllegalArgumentException("param " + index + " has to be a constant of type " + clazz);
            }
        } else {
            ExprEvaluator evaluator = (ExprEvaluator) validationContext.getExpressions()[index];
            Object obj = evaluator.evaluate(null, true, null);
            if (obj.getClass().equals(clazz)) {
                return obj;
            } else {
                throw new IllegalArgumentException("param " + index + " has to be a constant of type " + clazz);
            }
        }
    }
}
