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

        for (Substitution tuple : substitutions) {
            tuple.validate();

            final NameReplacement nameReplacement = new NameReplacement();
            nameReplacement.replacement = tuple.replacement;
            if (tuple.argType != null) {
                nameReplacement.type = forName(tuple.argType);
            }

            Map<String, List<NameReplacement>> replacementMap = regexMap.get(tuple.method);
            if (replacementMap == null) {
                replacementMap = new LinkedHashMap<String, List<NameReplacement>>();
                regexMap.put(tuple.method, replacementMap);
            }
            List<NameReplacement> replacements = replacementMap.get(tuple.argName);
            if (replacements == null) {
                replacements = new ArrayList<NameReplacement>();
                replacementMap.put(tuple.argName, replacements);
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
                if (methodEntry.getKey().matcher(model.getName()).matches()) {

                    // look for arg name matches
                    final List<Argument> updatedArguments = new ArrayList<Argument>();
                    final Map<Pattern, List<NameReplacement>> argMap = methodEntry.getValue();
                    for (Argument argument : model.getArguments()) {
                        for (Map.Entry<Pattern, List<NameReplacement>> argEntry : argMap.entrySet()) {
                            final Matcher matcher = argEntry.getKey().matcher(argument.getName());
                            if (matcher.find()) {
                                final List<NameReplacement> adapters = argEntry.getValue();
                                for (NameReplacement adapter : adapters) {
                                    if (adapter.type == null || adapter.type.isAssignableFrom(argument.getType())) {
                                        argument = new Argument(matcher.replaceAll(adapter.replacement), argument.getType());
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

    public static class Substitution {

        private String method;
        private String argName;
        private String argType;
        private String replacement;

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
            this.method = method;
            this.argName = argName;
            this.argType = argType;
            this.replacement = replacement;
        }

        public void validate() {
            if (method == null || argName == null || replacement == null) {
                throw new IllegalArgumentException("Properties method, argName and replacement MUST be provided");
            }
        }
    }

    private static class NameReplacement {
        private String replacement;
        private Class<?> type;
    }
}
