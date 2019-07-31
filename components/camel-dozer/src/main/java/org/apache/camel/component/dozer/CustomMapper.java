/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.dozer;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.spi.ClassResolver;

/**
 * Allows a user to customize a field mapping using a POJO that is not
 * required to extend/implement Dozer-specific classes.
 */
public class CustomMapper extends BaseConverter {

    private ClassResolver resolver;

    public CustomMapper(ClassResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Object convert(Object existingDestinationFieldValue,
                          Object sourceFieldValue,
                          Class<?> destinationClass,
                          Class<?> sourceClass) {
        try {
            return mapCustom(sourceFieldValue, sourceClass);
        } finally {
            done();
        }
    }

    private Object invokeFunction(Method method,
                                  Object customObj,
                                  Object source,
                                  String[][] parameters) throws Exception {
        Class<?>[] prmTypes = method.getParameterTypes();
        Object[] methodPrms = new Object[prmTypes.length];
        methodPrms[0] = source;
        for (int parameterNdx = 0, methodPrmNdx = 1; parameterNdx < parameters.length; parameterNdx++, methodPrmNdx++) {
            if (method.isVarArgs() && methodPrmNdx == prmTypes.length - 1) {
                Object array = Array.newInstance(prmTypes[methodPrmNdx].getComponentType(), parameters.length - parameterNdx);
                for (int arrayNdx = 0; parameterNdx < parameters.length; parameterNdx++, arrayNdx++) {
                    String[] parts = parameters[parameterNdx];
                    Array.set(array, arrayNdx, resolver.resolveClass(parts[0]).getConstructor(String.class).newInstance(parts[1]));
                }
                methodPrms[methodPrmNdx] = array;
            } else {
                String[] parts = parameters[parameterNdx];
                methodPrms[methodPrmNdx] = resolver.resolveClass(parts[0]).getConstructor(String.class).newInstance(parts[1]);
            }
        }
        return method.invoke(customObj, methodPrms);
    }

    Object mapCustom(Object source, Class<?> sourceClass) {
        // The converter parameter is stored in a thread local variable, so
        // we need to parse the parameter on each invocation
        // ex: custom-converter-param="org.example.MyMapping,map"
        // className = org.example.MyMapping
        // operation = map
        String[] prms = getParameter().split(",");
        String className = prms[0];
        String operation = prms.length > 1 ? prms[1] : null;

        // now attempt to process any additional parameters passed along
        // ex: custom-converter-param="org.example.MyMapping,substring,java.lang.Integer=3,java.lang.Integer=10"
        // className = org.example.MyMapping
        // operation = substring
        // parameters = ["java.lang.Integer=3","java.lang.Integer=10"]
        String[][] prmTypesAndValues;
        if (prms.length > 2) {
            // Break parameters down into types and values
            prmTypesAndValues = new String[prms.length - 2][2];
            for (int ndx = 0; ndx < prmTypesAndValues.length; ndx++) {
                String prm = prms[ndx + 2];
                String[] parts = prm.split("=");
                if (parts.length != 2) {
                    throw new RuntimeException("Value missing for parameter " + prm);
                }
                prmTypesAndValues[ndx][0] = parts[0];
                prmTypesAndValues[ndx][1] = parts[1];
            }
        } else {
            prmTypesAndValues = null;
        }

        Object customObj;
        Method method;
        try {
            Class<?> customClass = resolver.resolveMandatoryClass(className);
            customObj = customClass.newInstance();

            // If a specific mapping operation has been supplied use that
            if (operation != null && prmTypesAndValues != null) {
                method = selectMethod(customClass, operation, sourceClass, prmTypesAndValues);
            } else if (operation != null) {
                method = customClass.getMethod(operation, sourceClass);
            } else {
                method = selectMethod(customClass, sourceClass);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load custom function", e);
        }

        // Verify that we found a matching method
        if (method == null) {
            throw new RuntimeException("No eligible custom function methods in " + className);
        }

        // Invoke the custom mapping method
        try {
            if (prmTypesAndValues != null) {
                return invokeFunction(method, customObj, source, prmTypesAndValues);
            } else {
                return method.invoke(customObj, source);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while invoking custom function", e);
        }
    }

    private boolean parametersMatchParameterList(Class<?>[] prmTypes,
                                                 String[][] parameters) {
        int ndx = 0;
        while (ndx < prmTypes.length) {
            Class<?> prmType = prmTypes[ndx];
            if (ndx >= parameters.length) {
                return ndx == prmTypes.length - 1 && prmType.isArray();
            }
            if (ndx == prmTypes.length - 1 && prmType.isArray()) { // Assume this only occurs for functions with var args
                Class<?> varArgClass = prmType.getComponentType();
                while (ndx < parameters.length) {
                    Class<?> prmClass = resolver.resolveClass(parameters[ndx][0]);
                    if (!varArgClass.isAssignableFrom(prmClass)) {
                        return false;
                    }
                    ndx++;
                }
            } else {
                Class<?> prmClass = resolver.resolveClass(parameters[ndx][0]);
                if (!prmTypes[ndx].isAssignableFrom(prmClass)) {
                    return false;
                }
            }
            ndx++;
        }
        return true;
    }

    Method selectMethod(Class<?> customClass,
                        Class<?> sourceClass) {
        Method method = null;
        for (Method m : customClass.getDeclaredMethods()) {
            if (m.getReturnType() != null
                    && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0].isAssignableFrom(sourceClass)) {
                method = m;
                break;
            }
        }
        return method;
    }

    // Assumes source is a separate parameter in method even if it has var args and that there are no
    // ambiguous calls based upon number and types of parameters
    private Method selectMethod(Class<?> customClass,
                                String operation,
                                Class<?> sourceClass,
                                String[][] parameters) {
        // Create list of potential methods
        List<Method> methods = new ArrayList<>();
        for (Method method : customClass.getDeclaredMethods()) {
            methods.add(method);
        }

        // Remove methods that are not applicable
        for (Iterator<Method> iter = methods.iterator(); iter.hasNext();) {
            Method method = iter.next();
            Class<?>[] prmTypes = method.getParameterTypes();
            if (!method.getName().equals(operation)
                    || method.getReturnType() == null
                    || !prmTypes[0].isAssignableFrom(sourceClass)) {
                iter.remove();
                continue;
            }
            prmTypes = Arrays.copyOfRange(prmTypes, 1, prmTypes.length); // Remove source from type list
            if (!method.isVarArgs() && prmTypes.length != parameters.length) {
                iter.remove();
                continue;
            }
            if (!parametersMatchParameterList(prmTypes, parameters)) {
                iter.remove();
                continue;
            }
        }

        // If more than one method is applicable, return the method whose prm list exactly matches the parameters
        // if possible
        if (methods.size() > 1) {
            for (Method method : methods) {
                if (!method.isVarArgs()) {
                    return method;
                }
            }
        }

        return methods.size() > 0 ? methods.get(0) : null;
    }
}
