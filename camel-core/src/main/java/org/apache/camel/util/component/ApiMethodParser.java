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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser base class for generating ApiMethod enumerations.
 */
public abstract class ApiMethodParser<T> {

    // also used by JavadocApiMethodGeneratorMojo
    public static final Pattern ARGS_PATTERN = Pattern.compile("\\s*([^<\\s]+)\\s*(<[^>]+>)?\\s+([^\\s,]+)\\s*,?");

    private static final String METHOD_PREFIX = "^(\\s*(public|final|synchronized|native)\\s+)*(\\s*<[^>]>)?\\s*(\\S+)\\s+([^\\(]+\\s*)\\(";
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\s*([^<\\s]+)\\s*(<[^>]+>)?\\s+(\\S+)\\s*\\(\\s*([\\S\\s,]*)\\)\\s*;?\\s*");

    private static final String JAVA_LANG = "java.lang.";
    private static final Map<String, Class<?>> PRIMITIVE_TYPES;

    static {
        PRIMITIVE_TYPES = new HashMap<String, Class<?>>();
        PRIMITIVE_TYPES.put("int", Integer.TYPE);
        PRIMITIVE_TYPES.put("long", Long.TYPE);
        PRIMITIVE_TYPES.put("double", Double.TYPE);
        PRIMITIVE_TYPES.put("float", Float.TYPE);
        PRIMITIVE_TYPES.put("boolean", Boolean.TYPE);
        PRIMITIVE_TYPES.put("char", Character.TYPE);
        PRIMITIVE_TYPES.put("byte", Byte.TYPE);
        PRIMITIVE_TYPES.put("void", Void.TYPE);
        PRIMITIVE_TYPES.put("short", Short.TYPE);
    }


    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Class<T> proxyType;
    private List<String> signatures;
    private ClassLoader classLoader = ApiMethodParser.class.getClassLoader();

    public ApiMethodParser(Class<T> proxyType) {
        this.proxyType = proxyType;
    }

    public Class<T> getProxyType() {
        return proxyType;
    }

    public final List<String> getSignatures() {
        return signatures;
    }

    public final void setSignatures(List<String> signatures) {
        this.signatures = new ArrayList<String>();
        this.signatures.addAll(signatures);
    }

    public final ClassLoader getClassLoader() {
        return classLoader;
    }

    public final void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Parses the method signatures from {@code getSignatures()}.
     * @return list of Api methods as {@link ApiMethodModel}
     */
    public final List<ApiMethodModel> parse() {
        // parse sorted signatures and generate descriptions
        List<ApiMethodModel> result = new ArrayList<ApiMethodModel>();
        for (String signature : signatures) {

            // skip comment or empty lines
            if (signature.startsWith("##") || ObjectHelper.isEmpty(signature)) {
                continue;
            }

            // remove all modifiers and type parameters for method
            signature = signature.replaceAll(METHOD_PREFIX, "$4 $5(");
            // remove all final modifiers for arguments
            signature = signature.replaceAll("(\\(|,\\s*)final\\s+", "$1");
            // remove all redundant spaces in generic parameters
            signature = signature.replaceAll("\\s*<\\s*", "<").replaceAll("\\s*>", ">");

            log.debug("Processing {}", signature);

            final Matcher methodMatcher = METHOD_PATTERN.matcher(signature);
            if (!methodMatcher.matches()) {
                throw new IllegalArgumentException("Invalid method signature " + signature);
            }

            // ignore generic type parameters in result, if any
            final Class<?> resultType = forName(methodMatcher.group(1));
            final String name = methodMatcher.group(3);
            final String argSignature = methodMatcher.group(4);

            final List<ApiMethodArg> arguments = new ArrayList<ApiMethodArg>();
            final List<Class<?>> argTypes = new ArrayList<Class<?>>();

            final Matcher argsMatcher = ARGS_PATTERN.matcher(argSignature);
            while (argsMatcher.find()) {

                final Class<?> type = forName(argsMatcher.group(1));
                argTypes.add(type);

                final String typeArgsGroup = argsMatcher.group(2);
                final String typeArgs = typeArgsGroup != null
                    ? typeArgsGroup.substring(1, typeArgsGroup.length() - 1).replaceAll(" ", "") : null;
                arguments.add(new ApiMethodArg(argsMatcher.group(3), type, typeArgs));
            }

            Method method;
            try {
                method = proxyType.getMethod(name, argTypes.toArray(new Class<?>[argTypes.size()]));
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Method not found [" + signature + "] in type " + proxyType.getName());
            }
            result.add(new ApiMethodModel(name, resultType, arguments, method));
        }

        // allow derived classes to post process
        result = processResults(result);

        // check that argument names have the same type across methods
        Map<String, Class<?>> allArguments = new HashMap<String, Class<?>>();
        for (ApiMethodModel model : result) {
            for (ApiMethodArg argument : model.getArguments()) {
                String name = argument.getName();
                Class<?> argClass = allArguments.get(name);
                Class<?> type = argument.getType();
                if (argClass == null) {
                    allArguments.put(name, type);
                } else {
                    if (argClass != type) {
                        throw new IllegalArgumentException("Argument [" + name 
                                + "] is used in multiple methods with different types " 
                                + argClass.getCanonicalName() + ", " + type.getCanonicalName());
                    }
                }
            }
        }
        allArguments.clear();

        result.sort(new Comparator<ApiMethodModel>() {
            @Override
            public int compare(ApiMethodModel model1, ApiMethodModel model2) {
                final int nameCompare = model1.name.compareTo(model2.name);
                if (nameCompare != 0) {
                    return nameCompare;
                } else {

                    final int nArgs1 = model1.arguments.size();
                    final int nArgsCompare = nArgs1 - model2.arguments.size();
                    if (nArgsCompare != 0) {
                        return nArgsCompare;
                    } else {
                        // same number of args, compare arg names, kinda arbitrary to use alphabetized order
                        for (int i = 0; i < nArgs1; i++) {
                            final int argCompare = model1.arguments.get(i).getName().compareTo(model2.arguments.get(i).getName());
                            if (argCompare != 0) {
                                return argCompare;
                            }
                        }
                        // duplicate methods???
                        log.warn("Duplicate methods found [{}], [{}]", model1, model2);
                        return 0;
                    }
                }
            }
        });

        // assign unique names to every method model
        final Map<String, Integer> dups = new HashMap<String, Integer>();
        for (ApiMethodModel model : result) {
            // locale independent upper case conversion
            final String name = model.getName();
            final char[] upperCase = new char[name.length()];
            final char[] lowerCase = name.toCharArray();
            for (int i = 0; i < upperCase.length; i++) {
                upperCase[i] = Character.toUpperCase(lowerCase[i]);
            }
            String uniqueName = new String(upperCase);

            Integer suffix = dups.get(uniqueName);
            if (suffix == null) {
                dups.put(uniqueName, 1);
            } else {
                dups.put(uniqueName, suffix + 1);
                StringBuilder builder = new StringBuilder(uniqueName);
                builder.append("_").append(suffix);
                uniqueName = builder.toString();
            }
            model.uniqueName = uniqueName;
        }
        return result;
    }

    protected List<ApiMethodModel> processResults(List<ApiMethodModel> result) {
        return result;
    }

    protected Class<?> forName(String className) {
        try {
            return forName(className, classLoader);
        } catch (ClassNotFoundException e1) {
            throw new IllegalArgumentException("Error loading class " + className);
        }
    }

    public static Class<?> forName(String className, ClassLoader classLoader) throws ClassNotFoundException {
        Class<?> result = null;
        try {
            // lookup primitive types first
            result = PRIMITIVE_TYPES.get(className);
            if (result == null) {
                result = Class.forName(className, true, classLoader);
            }
        } catch (ClassNotFoundException e) {
            // check if array type
            if (className.endsWith("[]")) {
                final int firstDim = className.indexOf('[');
                final int nDimensions = (className.length() - firstDim) / 2;
                result = Array.newInstance(forName(className.substring(0, firstDim), classLoader), new int[nDimensions]).getClass();
            } else if (className.indexOf('.') != -1) {
                // try replacing last '.' with $ to look for inner classes
                String innerClass = className;
                while (result == null && innerClass.indexOf('.') != -1) {
                    int endIndex = innerClass.lastIndexOf('.');
                    innerClass = innerClass.substring(0, endIndex) + "$" + innerClass.substring(endIndex + 1);
                    try {
                        result = Class.forName(innerClass, true, classLoader);
                    } catch (ClassNotFoundException ignore) {
                        // ignore
                    }
                }
            }
            if (result == null && !className.startsWith(JAVA_LANG)) {
                // try loading from default Java package java.lang
                try {
                    result = forName(JAVA_LANG + className, classLoader);
                } catch (ClassNotFoundException ignore) {
                    // ignore
                }
            }
        }

        if (result == null) {
            throw new ClassNotFoundException(className);
        }

        return result;
    }

    public static final class ApiMethodModel {
        private final String name;
        private final Class<?> resultType;
        private final List<ApiMethodArg> arguments;
        private final Method method;

        private String uniqueName;

        protected ApiMethodModel(String name, Class<?> resultType, List<ApiMethodArg> arguments, Method method) {
            this.name = name;
            this.resultType = resultType;
            this.arguments = arguments;
            this.method = method;
        }

        protected ApiMethodModel(String uniqueName, String name, Class<?> resultType, List<ApiMethodArg> arguments, Method method) {
            this.name = name;
            this.uniqueName = uniqueName;
            this.resultType = resultType;
            this.arguments = arguments;
            this.method = method;
        }

        public String getUniqueName() {
            return uniqueName;
        }

        public String getName() {
            return name;
        }

        public Class<?> getResultType() {
            return resultType;
        }

        public Method getMethod() {
            return method;
        }

        public List<ApiMethodArg> getArguments() {
            return arguments;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(resultType.getName()).append(" ");
            builder.append(name).append("(");
            for (ApiMethodArg argument : arguments) {
                builder.append(argument.getType().getCanonicalName()).append(" ");
                builder.append(argument.getName()).append(", ");
            }
            if (!arguments.isEmpty()) {
                builder.delete(builder.length() - 2, builder.length());
            }
            builder.append(");");
            return builder.toString();
        }
    }
}
