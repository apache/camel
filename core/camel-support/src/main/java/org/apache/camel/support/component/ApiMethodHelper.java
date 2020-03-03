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
package org.apache.camel.support.component;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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
    private final Map<String, List<T>> methodMap;

    // maps method name to method arguments of the form Class type1, String name1, Class type2, String name2,...
    private final Map<String, List<Object>> argumentsMap;

    // maps argument name to argument type
    private final Map<String, Class<?>> validArguments;

    // maps aliases to actual method names
    private final Map<String, Set<String>> aliasesMap;

    // nullable args
    private final List<String> nullableArguments;

    /**
     * Create a helper to work with a {@link ApiMethod}, using optional method aliases.
     * @param apiMethodEnum {@link ApiMethod} enumeration class
     * @param aliases Aliases mapped to actual method names
     * @param nullableArguments names of arguments that default to null value
     */
    public ApiMethodHelper(Class<T> apiMethodEnum, Map<String, String> aliases, List<String> nullableArguments) {

        Map<String, List<T>> tmpMethodMap = new HashMap<>();
        Map<String, List<Object>> tmpArgumentsMap = new HashMap<>();
        Map<String, Class<?>> tmpValidArguments = new HashMap<>();
        Map<String, Set<String>> tmpAliasesMap = new HashMap<>();

        // validate ApiMethod Enum
        if (apiMethodEnum == null) {
            throw new IllegalArgumentException("ApiMethod enumeration cannot be null");
        }

        if (nullableArguments != null && !nullableArguments.isEmpty()) {
            this.nullableArguments = Collections.unmodifiableList(new ArrayList<>(nullableArguments));
        } else {
            this.nullableArguments = Collections.emptyList();
        }

        final Map<Pattern, String> aliasPatterns = new HashMap<>();
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (alias.getKey() == null || alias.getValue() == null) {
                throw new IllegalArgumentException("Alias pattern and replacement cannot be null");
            }
            aliasPatterns.put(Pattern.compile(alias.getKey()), alias.getValue());
        }

        LOG.debug("Processing {}", apiMethodEnum.getName());
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
                        builder.append(Character.toLowerCase(firstChar)).append(alias, 1, alias.length());
                        alias = builder.toString();
                    }
                    Set<String> names = tmpAliasesMap.get(alias);
                    if (names == null) {
                        names = new HashSet<>();
                        tmpAliasesMap.put(alias, names);
                    }
                    names.add(name);
                }
            }

            // map method name to Enum
            List<T> overloads = tmpMethodMap.get(name);
            if (overloads == null) {
                overloads = new ArrayList<>();
                tmpMethodMap.put(method.getName(), overloads);
            }
            overloads.add(method);

            // add arguments for this method
            List<Object> arguments = tmpArgumentsMap.get(name);
            if (arguments == null) {
                arguments = new ArrayList<>();
                tmpArgumentsMap.put(name, arguments);
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
                final Class<?> previousType = tmpValidArguments.get(argName);
                if (previousType != null && previousType != argType) {
                    throw new IllegalArgumentException(String.format(
                            "Argument %s has ambiguous types (%s, %s) across methods!",
                            name, previousType, argType));
                } else if (previousType == null) {
                    tmpValidArguments.put(argName, argType);
                }
            }

        }

        // validate nullableArguments
        if (!tmpValidArguments.keySet().containsAll(this.nullableArguments)) {
            List<String> unknowns = new ArrayList<>(this.nullableArguments);
            unknowns.removeAll(tmpValidArguments.keySet());
            throw new IllegalArgumentException("Unknown nullable arguments " + unknowns.toString());
        }

        // validate aliases
        for (Map.Entry<String, Set<String>> entry : tmpAliasesMap.entrySet()) {

            // look for aliases that match multiple methods
            final Set<String> methodNames = entry.getValue();
            if (methodNames.size() > 1) {

                // get mapped methods
                final List<T> aliasedMethods = new ArrayList<>();
                for (String methodName : methodNames) {
                    List<T> mappedMethods = tmpMethodMap.get(methodName);
                    aliasedMethods.addAll(mappedMethods);
                }

                // look for argument overlap
                for (T method : aliasedMethods) {
                    final List<String> argNames = new ArrayList<>(method.getArgNames());
                    argNames.removeAll(this.nullableArguments);

                    final Set<T> ambiguousMethods = new HashSet<>();
                    for (T otherMethod : aliasedMethods) {
                        if (method != otherMethod) {
                            final List<String> otherArgsNames = new ArrayList<>(otherMethod.getArgNames());
                            otherArgsNames.removeAll(this.nullableArguments);

                            if (argNames.equals(otherArgsNames)) {
                                ambiguousMethods.add(method);
                                ambiguousMethods.add(otherMethod);
                            }
                        }
                    }

                    if (!ambiguousMethods.isEmpty()) {
                        throw new IllegalArgumentException(
                                String.format("Ambiguous alias %s for methods %s", entry.getKey(), ambiguousMethods));
                    }
                }
            }
        }

        this.methodMap = Collections.unmodifiableMap(tmpMethodMap);
        this.argumentsMap = Collections.unmodifiableMap(tmpArgumentsMap);
        this.validArguments = Collections.unmodifiableMap(tmpValidArguments);
        this.aliasesMap = Collections.unmodifiableMap(tmpAliasesMap);

        LOG.debug("Found {} unique method names in {} methods", tmpMethodMap.size(), methods.length);
    }

    /**
     * Gets methods that match the given name and arguments.<p/>
     * Note that the args list is a required subset of arguments for returned methods.
     *
     * @param name case sensitive method name or alias to lookup
     * @return non-null unmodifiable list of methods that take all of the given arguments, empty if there is no match
     */
    public List<ApiMethod> getCandidateMethods(String name) {
        return getCandidateMethods(name, Collections.emptyList());
    }

    /**
     * Gets methods that match the given name and arguments.<p/>
     * Note that the args list is a required subset of arguments for returned methods.
     *
     * @param name case sensitive method name or alias to lookup
     * @param argNames unordered required argument names
     * @return non-null unmodifiable list of methods that take all of the given arguments, empty if there is no match
     */
    public List<ApiMethod> getCandidateMethods(String name, Collection<String> argNames) {
        List<T> methods = methodMap.get(name);
        if (methods == null) {
            if (aliasesMap.containsKey(name)) {
                methods = new ArrayList<>();
                for (String method : aliasesMap.get(name)) {
                    methods.addAll(methodMap.get(method));
                }
            }
        }
        if (methods == null) {
            LOG.debug("No matching method for method {}", name);
            return Collections.emptyList();
        }
        int nArgs = argNames != null ? argNames.size() : 0;
        if (nArgs == 0) {
            LOG.debug("Found {} methods for method {}", methods.size(), name);
            return Collections.unmodifiableList(methods);
        } else {
            final List<ApiMethod> filteredSet = filterMethods(methods, MatchType.SUBSET, argNames);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found {} filtered methods for {}",
                        filteredSet.size(), name + argNames.toString().replace('[', '(').replace(']', ')'));
            }
            return filteredSet;
        }
    }

    /**
     * Filters a list of methods to those that take the given set of arguments.
     *
     * @param methods list of methods to filter
     * @param matchType whether the arguments are an exact match, a subset or a super set of method args
     * @return methods with arguments that satisfy the match type.<p/>
     * For SUPER_SET match, if methods with exact match are found, methods that take a subset are ignored
     */
    public List<ApiMethod> filterMethods(List<? extends ApiMethod> methods, MatchType matchType) {
        return filterMethods(methods, matchType, Collections.emptyList());
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
    public List<ApiMethod> filterMethods(List<? extends ApiMethod> methods, MatchType matchType, Collection<String> argNames) {
        // original arguments
        // supplied arguments with missing nullable arguments
        final List<String> withNullableArgsList;
        if (!nullableArguments.isEmpty()) {
            withNullableArgsList = new ArrayList<>(argNames);
            withNullableArgsList.addAll(nullableArguments);
        } else {
            withNullableArgsList = null;
        }

        // list of methods that have all args in the given names
        List<ApiMethod> result = new ArrayList<>();
        List<ApiMethod> extraArgs = null;
        List<ApiMethod> nullArgs = null;

        for (ApiMethod method : methods) {
            final List<String> methodArgs = method.getArgNames();
            switch (matchType) {
                case EXACT:
                    // method must take all args, and no more
                    if (methodArgs.containsAll(argNames) && argNames.containsAll(methodArgs)) {
                        result.add(method);
                    }
                    break;
                case SUBSET:
                    // all args are required, method may take more
                    if (methodArgs.containsAll(argNames)) {
                        result.add(method);
                    }
                    break;
                default:
                case SUPER_SET:
                    // all method args must be present
                    if (argNames.containsAll(methodArgs)) {
                        if (methodArgs.containsAll(argNames)) {
                            // prefer exact match to avoid unused args
                            result.add(method);
                        } else if (result.isEmpty()) {
                            // if result is empty, add method to extra args list
                            if (extraArgs == null) {
                                extraArgs = new ArrayList<>();
                            }
                            // method takes a subset, unused args
                            extraArgs.add(method);
                        }
                    } else if (result.isEmpty() && extraArgs == null) {
                        // avoid looking for nullable args by checking for empty result and extraArgs
                        if (withNullableArgsList != null && withNullableArgsList.containsAll(methodArgs)) {
                            if (nullArgs == null) {
                                nullArgs = new ArrayList<>();
                            }
                            nullArgs.add(method);
                        }
                    }
                    break;
            }
        }

        List<ApiMethod> methodList = result.isEmpty()
                ? extraArgs == null
                ? nullArgs
                : extraArgs
                : result;

        // preference order is exact match, matches with extra args, matches with null args
        return methodList != null ? Collections.unmodifiableList(methodList) : Collections.emptyList();
    }

    /**
     * Gets argument types and names for all overloaded methods and aliases with the given name.
     * @param name method name, either an exact name or an alias, exact matches are checked first
     * @return list of arguments of the form Class type1, String name1, Class type2, String name2,...
     */
    public List<Object> getArguments(final String name) throws IllegalArgumentException {
        List<Object> arguments = argumentsMap.get(name);
        if (arguments == null) {
            if (aliasesMap.containsKey(name)) {
                arguments = new ArrayList<>();
                for (String method : aliasesMap.get(name)) {
                    arguments.addAll(argumentsMap.get(method));
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
     * Returns alias map.
     * @return alias names mapped to method names.
     */
    public Map<String, Set<String>> getAliases() {
        return aliasesMap;
    }

    /**
     * Returns argument types and names used by all methods.
     * @return map with argument names as keys, and types as values
     */
    public Map<String, Class<?>> allArguments() {
        return validArguments;
    }

    /**
     * Returns argument names that can be set to null if not specified.
     * @return list of argument names
     */
    public List<String> getNullableArguments() {
        return nullableArguments;
    }

    /**
     * Get the type for the given argument name.
     * @param argName argument name
     * @return argument type
     */
    public Class<?> getType(String argName) throws IllegalArgumentException {
        final Class<?> type = validArguments.get(argName);
        if (type == null) {
            throw new IllegalArgumentException(argName);
        }
        return type;
    }

    // this method is always called with Enum value lists, so the cast inside is safe
    // the alternative of trying to convert ApiMethod and associated classes to generic classes would a bear!!!
    @SuppressWarnings("unchecked")
    public static ApiMethod getHighestPriorityMethod(List<? extends ApiMethod> filteredMethods) {
        Comparable<ApiMethod> highest = null;
        for (ApiMethod method : filteredMethods) {
            if (highest == null || highest.compareTo(method) <= 0) {
                highest = (Comparable<ApiMethod>)method;
            }
        }
        return (ApiMethod)highest;
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
    public static Object invokeMethod(Object proxy, ApiMethod method, Map<String, Object> properties)
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

    public enum MatchType {
        EXACT, SUBSET, SUPER_SET
    }

}
