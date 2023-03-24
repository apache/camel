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
package org.apache.camel.component.facebook.data;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import facebook4j.Facebook;
import facebook4j.FacebookException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.facebook.config.FacebookNameStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for working with {@link FacebookMethodsType}.
 */
public final class FacebookMethodsTypeHelper {

    private static final Logger LOG = LoggerFactory.getLogger(FacebookMethodsTypeHelper.class);

    // maps method name to FacebookMethodsType
    private static final Map<String, List<FacebookMethodsType>> METHOD_MAP = new HashMap<>();

    // maps method name to method arguments of the form Class type1, String name1, Class type2, String name2,...
    private static final Map<String, List<Object>> ARGUMENTS_MAP = new HashMap<>();

    // maps argument name to argument type
    private static final Map<String, Class<?>> VALID_ARGUMENTS = new HashMap<>();

    static {
        final FacebookMethodsType[] methods = FacebookMethodsType.values();
        // load lookup maps for FacebookMethodsType
        for (FacebookMethodsType method : methods) {

            // map method name to Enum
            final String name = method.getName();
            List<FacebookMethodsType> overloads = METHOD_MAP.get(name);
            if (overloads == null) {
                overloads = new ArrayList<>();
                METHOD_MAP.put(method.getName(), overloads);
            }
            overloads.add(method);

            // add arguments for this method
            List<Object> arguments = ARGUMENTS_MAP.get(name);
            if (arguments == null) {
                arguments = new ArrayList<>();
                ARGUMENTS_MAP.put(name, arguments);
            }

            // process all arguments for this method
            final int nArgs = method.getArgNames().size();
            final String[] argNames = method.getArgNames().toArray(new String[nArgs]);
            final Class<?>[] argTypes = method.getArgTypes().toArray(new Class[nArgs]);
            for (int i = 0; i < nArgs; i++) {
                final String argName = argNames[i];
                final Class<?> argType = argTypes[i];
                if (!arguments.contains(argName)) {
                    arguments.add(argType);
                    arguments.add(argName);
                }

                // also collect argument names for all methods, also detect clashes here
                final Class<?> previousType = VALID_ARGUMENTS.get(argName);
                if (previousType != null && previousType != argType) {
                    throw new ExceptionInInitializerError(
                            String.format(
                                    "Argument %s has ambiguous types (%s, %s) across methods!",
                                    name, previousType, argType));
                } else if (previousType == null) {
                    VALID_ARGUMENTS.put(argName, argType);
                }
            }

        }

        LOG.debug("Found {} unique method names in {} methods", METHOD_MAP.size(), methods.length);

    }

    private FacebookMethodsTypeHelper() {
    }

    /**
     * Gets methods that match the given name and arguments.
     * <p/>
     * Note that the args list is a required subset of arguments for returned methods.
     *
     * @param  name     case sensitive full method name to lookup
     * @param  argNames unordered required argument names
     * @return          non-null unmodifiable list of methods that take all of the given arguments, empty if there is no
     *                  match
     */
    public static List<FacebookMethodsType> getCandidateMethods(String name, String... argNames) {
        final List<FacebookMethodsType> methods = METHOD_MAP.get(name);
        if (methods == null) {
            LOG.debug("No matching method for method {}", name);
            return Collections.emptyList();
        }
        int nArgs = argNames != null ? argNames.length : 0;
        if (nArgs == 0) {
            LOG.debug("Found {} methods for method {}", methods.size(), name);
            return Collections.unmodifiableList(methods);
        } else {
            final List<FacebookMethodsType> filteredSet = filterMethods(methods, MatchType.SUBSET, argNames);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found {} filtered methods for {}",
                        filteredSet.size(), name + Arrays.toString(argNames).replace('[', '(').replace(']', ')'));
            }
            return filteredSet;
        }
    }

    /**
     * Filters a list of methods to those that take the given set of arguments.
     *
     * @param  methods   list of methods to filter
     * @param  matchType whether the arguments are an exact match, a subset or a super set of method args
     * @param  argNames  argument names to filter the list
     * @return           methods with arguments that satisfy the match type.
     *                   <p/>
     *                   For SUPER_SET match, if methods with exact match are found, methods that take a subset are
     *                   ignored
     */
    public static List<FacebookMethodsType> filterMethods(
            List<FacebookMethodsType> methods, MatchType matchType,
            String... argNames) {
        List<String> argsList = Arrays.asList(argNames);
        // list of methods that have all args in the given names
        final List<FacebookMethodsType> result = new ArrayList<>();
        final List<FacebookMethodsType> extraArgs = new ArrayList<>();

        for (FacebookMethodsType method : methods) {
            final List<String> methodArgs = method.getArgNames();
            switch (matchType) {
                case EXACT:
                    // method must take all args, and no more
                    if (methodArgs.containsAll(argsList) && argsList.containsAll(methodArgs)) {
                        result.add(method);
                    }
                    break;
                case SUBSET:
                    // all args are required, method may take more
                    if (methodArgs.containsAll(argsList)) {
                        result.add(method);
                    }
                    break;
                default:
                case SUPER_SET:
                    // all method args must be present
                    if (argsList.containsAll(methodArgs)) {
                        if (methodArgs.containsAll(argsList)) {
                            // prefer exact match to avoid unused args
                            result.add(method);
                        } else {
                            // method takes a subset, unused args
                            extraArgs.add(method);
                        }
                    }
                    break;
            }
        }

        return Collections.unmodifiableList(result.isEmpty() ? extraArgs : result);
    }

    /**
     * Gets argument types and names for all overloaded methods with the given name.
     *
     * @param  name method name, must be a long form (i.e. get*, or search*)
     * @return      list of arguments of the form Class type1, String name1, Class type2, String name2,...
     */
    public static List<Object> getArguments(String name) throws IllegalArgumentException {
        final List<Object> arguments = ARGUMENTS_MAP.get(name);
        if (arguments == null) {
            throw new IllegalArgumentException(name);
        }
        return Collections.unmodifiableList(arguments);
    }

    /**
     * Gets argument types and names for all overloaded methods with the given short form name.
     *
     * @param  name  method name, may be a short form
     * @param  style name style
     * @return       list of arguments of the form Class type1, String name1, Class type2, String name2,...
     */
    public static List<Object> getArgumentsForNameStyle(String name, FacebookNameStyle style) throws IllegalArgumentException {
        if (style == null) {
            throw new IllegalArgumentException("Parameters style cannot be null");
        }
        switch (style) {
            case EXACT:
                return getArguments(name);
            case GET:
                return getArguments(convertToGetMethod(name));
            case SEARCH:
                return getArguments(convertToSearchMethod(name));
            case GET_AND_SEARCH:
            default:
                final List<Object> arguments = new ArrayList<>();
                arguments.addAll(getArguments(convertToGetMethod(name)));
                arguments.addAll(getArguments(convertToSearchMethod(name)));
                return Collections.unmodifiableList(arguments);
        }
    }

    /**
     * Get missing properties.
     *
     * @param  methodName method name
     * @param  nameStyle  method name style
     * @param  argNames   available arguments
     * @return            Set of missing argument names
     */
    public static Set<String> getMissingProperties(String methodName, FacebookNameStyle nameStyle, Set<String> argNames) {
        final List<Object> argsWithTypes = getArgumentsForNameStyle(methodName, nameStyle);
        final Set<String> missingArgs = new HashSet<>();

        for (int i = 1; i < argsWithTypes.size(); i += 2) {
            final String name = (String) argsWithTypes.get(i);
            if (!argNames.contains(name)) {
                missingArgs.add(name);
            }
        }

        return missingArgs;
    }

    /**
     * Get argument types and names used by all methods.
     *
     * @return map with argument names as keys, and types as values
     */
    public static Map<String, Class<?>> allArguments() {
        return Collections.unmodifiableMap(VALID_ARGUMENTS);
    }

    /**
     * Get the type for the given argument name.
     *
     * @param  argName argument name
     * @return         argument type
     */
    public static Class<?> getType(String argName) throws IllegalArgumentException {
        final Class<?> type = VALID_ARGUMENTS.get(argName);
        if (type == null) {
            throw new IllegalArgumentException(argName);
        }
        return type;
    }

    public static String convertToGetMethod(String name) throws IllegalArgumentException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static String convertToSearchMethod(String name) throws IllegalArgumentException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        return "search" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static FacebookMethodsType getHighestPriorityMethod(List<FacebookMethodsType> filteredMethods) {
        FacebookMethodsType highest = null;
        for (FacebookMethodsType method : filteredMethods) {
            if (highest == null || method.ordinal() > highest.ordinal()) {
                highest = method;
            }
        }
        return highest;
    }

    /**
     * Invokes given method with argument values from given properties.
     *
     * @param  facebook              Facebook4J target object for invoke
     * @param  method                method to invoke
     * @param  properties            Map of arguments
     * @return                       result of method invocation
     * @throws RuntimeCamelException on errors
     */
    public static Object invokeMethod(Facebook facebook, FacebookMethodsType method, Map<String, Object> properties)
            throws RuntimeCamelException {

        LOG.debug("Invoking {} with arguments {}", method.getName(), properties);

        final List<String> argNames = method.getArgNames();
        final Object[] values = new Object[argNames.size()];
        final List<Class<?>> argTypes = method.getArgTypes();
        final Class<?>[] types = argTypes.toArray(new Class[0]);
        int index = 0;
        for (String name : argNames) {
            Object value = properties.get(name);

            // is the parameter an array type?
            if (value != null && types[index].isArray()) {
                Class<?> type = types[index];

                if (value instanceof Collection) {
                    // convert collection to array
                    Collection<?> collection = (Collection<?>) value;
                    Object array = Array.newInstance(type.getComponentType(), collection.size());
                    if (array instanceof Object[]) {
                        collection.toArray((Object[]) array);
                    } else {
                        int i = 0;
                        for (Object el : collection) {
                            Array.set(array, i++, el);
                        }
                    }
                    value = array;
                } else if (value.getClass().isArray()
                        && type.getComponentType().isAssignableFrom(value.getClass().getComponentType())) {
                    // convert derived array to super array
                    final int size = Array.getLength(value);
                    Object array = Array.newInstance(type.getComponentType(), size);
                    for (int i = 0; i < size; i++) {
                        Array.set(array, i, Array.get(value, i));
                    }
                    value = array;
                } else {
                    throw new IllegalArgumentException(
                            String.format("Cannot convert %s to %s", value.getClass(), type));
                }
            }

            values[index++] = value;
        }

        try {
            return method.getMethod().invoke(facebook, values);
        } catch (Exception e) {
            // skip wrapper exception to simplify stack
            String msg;
            if (e.getCause() instanceof FacebookException) {
                msg = ((FacebookException) e.getCause()).getErrorMessage();
            } else {
                msg = e.getMessage();
            }
            throw new RuntimeCamelException(
                    String.format("Error invoking %s with %s: %s", method.getName(), properties, msg), e);
        }
    }

    public enum MatchType {
        EXACT,
        SUBSET,
        SUPER_SET
    }

}
