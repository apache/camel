/**
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
package org.apache.camel.util.component;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for working with {@link ApiMethod}.
 */
public final class ApiMethodHelper<T extends Enum<T> & ApiMethod> {

    private static final Logger LOG = LoggerFactory.getLogger(ApiMethodHelper.class);

    // maps method name to ApiMethod
    private final Map<String, List<T>> METHOD_MAP = new HashMap<String, List<T>>();

    // maps method name to method arguments of the form Class type1, String name1, Class type2, String name2,...
    private final Map<String, List<Object>> ARGUMENTS_MAP = new HashMap<String, List<Object>>();

    // maps argument name to argument type
    private final Map<String, Class<?>> VALID_ARGUMENTS = new HashMap<String, Class<?>>();

    // maps aliases to actual method names
    private final HashMap<String, Set<String>> ALIASES = new HashMap<String, Set<String>>();

    /**
     * Create a helper to work with a {@link ApiMethod}, using optional method aliases.
     * @param apiMethodEnum {@link ApiMethod} enumeration class
     * @param aliases Aliases mapped to actual method names
     */
    public ApiMethodHelper(Class<T> apiMethodEnum, Map<String, String> aliases) {

        // validate ApiMethod Enum
        if (apiMethodEnum == null) {
            throw new IllegalArgumentException("ApiMethod enumeration cannot be null");
        }

        final Map<Pattern, String> aliasPatterns = new HashMap<Pattern, String>();
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (alias.getKey() == null || alias.getValue() == null) {
                throw new IllegalArgumentException("Alias pattern and replacement cannot be null");
            }
            aliasPatterns.put(Pattern.compile(alias.getKey()), alias.getValue());
        }

        LOG.debug("Processing " + apiMethodEnum.getName());
        final T[] methods = apiMethodEnum.getEnumConstants();

        // load lookup maps
        for (T method : methods) {

            final String name = method.getName();

            // add method name aliases
            for (Map.Entry<Pattern, String> aliasEntry : aliasPatterns.entrySet()) {
                final Matcher matcher = aliasEntry.getKey().matcher(name);
                if (matcher.find()) {
                    // add method name alias
                    String alias = matcher.replaceAll(aliasEntry.getValue());
                    // convert first character to lowercase
                    assert alias.length() > 1;
                    final char firstChar = alias.charAt(0);
                    if (!Character.isLowerCase(firstChar)) {
                        final StringBuilder builder = new StringBuilder();
                        builder.append(Character.toLowerCase(firstChar)).append(alias.substring(1));
                        alias = builder.toString();
                    }
                    Set<String> names = ALIASES.get(alias);
                    if (names == null) {
                        names = new HashSet<String>();
                        ALIASES.put(alias, names);
                    }
                    names.add(name);
                }
            }

            // map method name to Enum
            List<T> overloads = METHOD_MAP.get(name);
            if (overloads == null) {
                overloads = new ArrayList<T>();
                METHOD_MAP.put(method.getName(), overloads);
            }
            overloads.add(method);

            // add arguments for this method
            List<Object> arguments = ARGUMENTS_MAP.get(name);
            if (arguments == null) {
                arguments = new ArrayList<Object>();
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

                // also collect argument names for all methods, and detect clashes here
                final Class<?> previousType = VALID_ARGUMENTS.get(argName);
                if (previousType != null && previousType != argType) {
                    throw new IllegalArgumentException(String.format(
                        "Argument %s has ambiguous types (%s, %s) across methods!",
                        name, previousType, argType));
                } else if (previousType == null) {
                    VALID_ARGUMENTS.put(argName, argType);
                }
            }

        }

        LOG.debug("Found {} unique method names in {} methods", METHOD_MAP.size(), methods.length);
    }

    /**
     * Gets methods that match the given name and arguments.<p/>
     * Note that the args list is a required subset of arguments for returned methods.
     * @param name case sensitive method name or alias to lookup
     * @param argNames unordered required argument names
     * @return non-null unmodifiable list of methods that take all of the given arguments, empty if there is no match
     */
    public List<T> getCandidateMethods(String name, String... argNames) {
        List<T> methods = METHOD_MAP.get(name);
        if (methods == null) {
            if (ALIASES.containsKey(name)) {
                methods = new ArrayList<T>();
                for (String method : ALIASES.get(name)) {
                    methods.addAll(METHOD_MAP.get(method));
                }
            }
        }
        if (methods == null) {
            LOG.debug("No matching method for method {}", name);
            return Collections.emptyList();
        }
        int nArgs = argNames != null ? argNames.length : 0;
        if (nArgs == 0) {
            LOG.debug("Found {} methods for method {}", methods.size(), name);
            return Collections.unmodifiableList(methods);
        } else {
            final List<T> filteredSet = filterMethods(methods, MatchType.SUBSET, argNames);
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
     * @param methods list of methods to filter
     * @param matchType whether the arguments are an exact match, a subset or a super set of method args
     * @param argNames argument names to filter the list
     * @return methods with arguments that satisfy the match type.<p/>
     * For SUPER_SET match, if methods with exact match are found, methods that take a subset are ignored
     */
    public List<T> filterMethods(List<T> methods, MatchType matchType,
                                                          String... argNames) {
        List<String> argsList = Arrays.asList(argNames);
        // list of methods that have all args in the given names
        final List<T> result = new ArrayList<T>();
        final List<T> extraArgs = new ArrayList<T>();

        for (T method : methods) {
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
     * Gets argument types and names for all overloaded methods and aliases with the given name.
     * @param name method name, either an exact name or an alias, exact matches are checked first
     * @return list of arguments of the form Class type1, String name1, Class type2, String name2,...
     */
    public List<Object> getArguments(final String name) throws IllegalArgumentException {
        List<Object> arguments = ARGUMENTS_MAP.get(name);
        if (arguments == null) {
            if (ALIASES.containsKey(name)) {
                arguments = new ArrayList<Object>();
                for (String method : ALIASES.get(name)) {
                    arguments.addAll(ARGUMENTS_MAP.get(method));
                }
            }
        }
        if (arguments == null) {
            throw new IllegalArgumentException(name);
        }
        return Collections.unmodifiableList(arguments);
    }

    /**
     * Get missing properties.
     * @param methodName method name
     * @param argNames available arguments
     * @return Set of missing argument names
     */
    public Set<String> getMissingProperties(String methodName, Set<String> argNames) {
        final List<Object> argsWithTypes = getArguments(methodName);
        final Set<String> missingArgs = new HashSet<String>();

        for (int i = 1; i < argsWithTypes.size(); i += 2) {
            final String name = (String) argsWithTypes.get(i);
            if (!argNames.contains(name)) {
                missingArgs.add(name);
            }
        }

        return missingArgs;
    }

    /**
     * Returns alias map.
     * @return alias names mapped to method names.
     */
    public Map<String, Set<String>> getAliases() {
        return Collections.unmodifiableMap(ALIASES);
    }

    /**
     * Get argument types and names used by all methods.
     * @return map with argument names as keys, and types as values
     */
    public Map<String, Class<?>> allArguments() {
        return Collections.unmodifiableMap(VALID_ARGUMENTS);
    }

    /**
     * Get the type for the given argument name.
     * @param argName argument name
     * @return argument type
     */
    public Class<?> getType(String argName) throws IllegalArgumentException {
        final Class<?> type = VALID_ARGUMENTS.get(argName);
        if (type == null) {
            throw new IllegalArgumentException(argName);
        }
        return type;
    }

    public T getHighestPriorityMethod(List<T> filteredMethods) {
        T highest = null;
        for (T method : filteredMethods) {
            if (highest == null || method.compareTo(highest) > 0) {
                highest = method;
            }
        }
        return highest;
    }

    /**
     * Invokes given method with argument values from given properties.
     *
     * @param proxy Proxy object for invoke
     * @param method method to invoke
     * @param properties Map of arguments
     * @return result of method invocation
     * @throws org.apache.camel.RuntimeCamelException on errors
     */
    public Object invokeMethod(Object proxy, T method, Map<String, Object> properties)
        throws RuntimeCamelException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Invoking {} with arguments {}", method.getName(), properties);
        }

        final List<String> argNames = method.getArgNames();
        final Object[] values = new Object[argNames.size()];
        final List<Class<?>> argTypes = method.getArgTypes();
        final Class<?>[] types = argTypes.toArray(new Class[argTypes.size()]);
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
                    // convert derived array to super array if needed
                    if (type.getComponentType() != value.getClass().getComponentType()) {
                        final int size = Array.getLength(value);
                        Object array = Array.newInstance(type.getComponentType(), size);
                        for (int i = 0; i < size; i++) {
                            Array.set(array, i, Array.get(value, i));
                        }
                        value = array;
                    }
                } else {
                    throw new IllegalArgumentException(
                        String.format("Cannot convert %s to %s", value.getClass(), type));
                }
            }

            values[index++] = value;
        }

        try {
            return method.getMethod().invoke(proxy, values);
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                // get API exception
                final Throwable cause = e.getCause();
                e = (cause != null) ? cause : e;
            }
            throw new RuntimeCamelException(
                String.format("Error invoking %s with %s: %s", method.getName(), properties, e.getMessage()), e);
        }
    }

    public static enum MatchType {
        EXACT, SUBSET, SUPER_SET
    }

}
