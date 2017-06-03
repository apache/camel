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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds support for parameter name substitutions.
 */
public class ArgumentSubstitutionParser<T> extends ApiMethodParser<T> {

    private final Map<Pattern, Map<Pattern, List<NameReplacement>>> methodMap;

    /**
     * Create a parser using regular expressions to adapt parameter names.
     * @param proxyType Proxy class.
     * @param substitutions an array of <b>ordered</b> Argument adapters.
     */
    public ArgumentSubstitutionParser(Class<T> proxyType, Substitution[] substitutions) {
        super(proxyType);
        Map<String, Map<String, List<NameReplacement>>> regexMap = new LinkedHashMap<String, Map<String, List<NameReplacement>>>();

        for (Substitution substitution : substitutions) {
            substitution.validate();

            final NameReplacement nameReplacement = new NameReplacement();
            nameReplacement.replacement = substitution.replacement;
            if (substitution.argType != null) {
                nameReplacement.typePattern = Pattern.compile(substitution.argType);
            }
            nameReplacement.replaceWithType = substitution.replaceWithType;

            Map<String, List<NameReplacement>> replacementMap = regexMap.get(substitution.method);
            if (replacementMap == null) {
                replacementMap = new LinkedHashMap<String, List<NameReplacement>>();
                regexMap.put(substitution.method, replacementMap);
            }
            List<NameReplacement> replacements = replacementMap.get(substitution.argName);
            if (replacements == null) {
                replacements = new ArrayList<NameReplacement>();
                replacementMap.put(substitution.argName, replacements);
            }
            replacements.add(nameReplacement);
        }

        // now compile the patterns, all this because Pattern doesn't override equals()!!!
        this.methodMap = new LinkedHashMap<Pattern, Map<Pattern, List<NameReplacement>>>();
        for (Map.Entry<String, Map<String, List<NameReplacement>>> method : regexMap.entrySet()) {
            Map<Pattern, List<NameReplacement>> argMap = new LinkedHashMap<Pattern, List<NameReplacement>>();
            for (Map.Entry<String, List<NameReplacement>> arg : method.getValue().entrySet()) {
                argMap.put(Pattern.compile(arg.getKey()), arg.getValue());
            }
            methodMap.put(Pattern.compile(method.getKey()), argMap);
        }
    }

    @Override
    public List<ApiMethodModel> processResults(List<ApiMethodModel> parseResult) {
        final List<ApiMethodModel> result = new ArrayList<ApiMethodModel>();

        for (ApiMethodModel model : parseResult) {
            // look for method name matches
            for (Map.Entry<Pattern, Map<Pattern, List<NameReplacement>>> methodEntry : methodMap.entrySet()) {
                // match the whole method name
                if (methodEntry.getKey().matcher(model.getName()).matches()) {

                    // look for arg name matches
                    final List<ApiMethodArg> updatedArguments = new ArrayList<ApiMethodArg>();
                    final Map<Pattern, List<NameReplacement>> argMap = methodEntry.getValue();
                    for (ApiMethodArg argument : model.getArguments()) {

                        final Class<?> argType = argument.getType();
                        final String typeArgs = argument.getTypeArgs();
                        final String argTypeName = argType.getCanonicalName();

                        for (Map.Entry<Pattern, List<NameReplacement>> argEntry : argMap.entrySet()) {
                            final Matcher matcher = argEntry.getKey().matcher(argument.getName());

                            // match argument name substring
                            if (matcher.find()) {
                                final List<NameReplacement> adapters = argEntry.getValue();
                                for (NameReplacement adapter : adapters) {
                                    if (adapter.typePattern == null) {

                                        // no type pattern
                                        final String newName = getJavaArgName(matcher.replaceAll(adapter.replacement));
                                        argument = new ApiMethodArg(newName, argType, typeArgs);

                                    } else {

                                        final Matcher typeMatcher = adapter.typePattern.matcher(argTypeName);
                                        if (typeMatcher.find()) {
                                            if (!adapter.replaceWithType) {
                                                // replace argument name
                                                final String newName = getJavaArgName(matcher.replaceAll(adapter.replacement));
                                                argument = new ApiMethodArg(newName, argType, typeArgs);
                                            } else {
                                                // replace name with argument type name
                                                final String newName = getJavaArgName(typeMatcher.replaceAll(adapter.replacement));
                                                argument = new ApiMethodArg(newName, argType, typeArgs);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        updatedArguments.add(argument);
                    }

                    model = new ApiMethodModel(model.getUniqueName(), model.getName(), model.getResultType(),
                            updatedArguments, model.getMethod());
                }
            }

            result.add(model);
        }

        return result;
    }

    private String getJavaArgName(String name) {
        // make sure the first character is lowercase
        // useful for replacement using type names
        char firstChar = name.charAt(0);
        if (Character.isLowerCase(firstChar)) {
            return name;
        } else {
            return Character.toLowerCase(firstChar) + name.substring(1);
        }
    }

    public static class Substitution {

        private String method;
        private String argName;
        private String argType;
        private String replacement;
        private boolean replaceWithType;

        /**
         * Creates a substitution for all argument types.
         * @param method regex to match method name
         * @param argName regex to match argument name
         * @param replacement replacement text for argument name
         */
        public Substitution(String method, String argName, String replacement) {
            this.method = method;
            this.argName = argName;
            this.replacement = replacement;
        }

        /**
         * Creates a substitution for a specific argument type.
         * @param method regex to match method name
         * @param argName regex to match argument name
         * @param argType argument type as String
         * @param replacement replacement text for argument name
         */
        public Substitution(String method, String argName, String argType, String replacement) {
            this(method, argName, replacement);
            this.argType = argType;
        }

        /**
         * Create a substitution for a specific argument type and flag to indicate whether the replacement uses
         * @param method
         * @param argName
         * @param argType
         * @param replacement
         * @param replaceWithType
         */
        public Substitution(String method, String argName, String argType, String replacement, boolean replaceWithType) {
            this(method, argName, argType, replacement);
            this.replaceWithType = replaceWithType;
        }

        public void validate() {
            if (method == null || argName == null || replacement == null) {
                throw new IllegalArgumentException("Properties method, argName and replacement MUST be provided");
            }
        }
    }

    private static class NameReplacement {
        private String replacement;
        private Pattern typePattern;
        private boolean replaceWithType;
    }
}
