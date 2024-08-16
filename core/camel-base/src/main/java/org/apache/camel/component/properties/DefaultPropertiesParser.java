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
package org.apache.camel.component.properties;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.PropertiesLookupListener;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.spi.PropertiesComponent.OPTIONAL_TOKEN;
import static org.apache.camel.spi.PropertiesComponent.PREFIX_TOKEN;
import static org.apache.camel.spi.PropertiesComponent.SUFFIX_TOKEN;
import static org.apache.camel.util.IOHelper.lookupEnvironmentVariable;

/**
 * A parser to parse a string which contains property placeholders.
 */
public class DefaultPropertiesParser implements PropertiesParser {

    private static final String UNRESOLVED_PREFIX_TOKEN = "@@[";

    private static final String UNRESOLVED_SUFFIX_TOKEN = "]@@";

    private static final String GET_OR_ELSE_TOKEN = ":";

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private PropertiesComponent propertiesComponent;

    public DefaultPropertiesParser() {
    }

    public DefaultPropertiesParser(PropertiesComponent propertiesComponent) {
        this.propertiesComponent = propertiesComponent;
    }

    public PropertiesComponent getPropertiesComponent() {
        return propertiesComponent;
    }

    public void setPropertiesComponent(PropertiesComponent propertiesComponent) {
        this.propertiesComponent = propertiesComponent;
    }

    @Override
    public String parseUri(
            String text, PropertiesLookup properties, boolean defaultFallbackEnabled, boolean keepUnresolvedOptional,
            boolean nestedPlaceholder)
            throws IllegalArgumentException {
        ParsingContext context
                = new ParsingContext(properties, defaultFallbackEnabled, keepUnresolvedOptional, nestedPlaceholder);
        String answer = context.parse(text);
        if (keepUnresolvedOptional && answer != null && answer.contains(UNRESOLVED_PREFIX_TOKEN)) {
            // replace temporary unresolved keys back to with placeholders so they are kept as-is
            answer = answer.replace(UNRESOLVED_PREFIX_TOKEN, PREFIX_TOKEN);
            answer = answer.replace(UNRESOLVED_SUFFIX_TOKEN, SUFFIX_TOKEN);
        }
        return answer;
    }

    @Override
    public String parseProperty(String key, String value, PropertiesLookup properties) {
        return value;
    }

    /**
     * This inner class helps replacing properties.
     */
    private final class ParsingContext {
        private final PropertiesLookup properties;
        private final boolean defaultFallbackEnabled;
        private final boolean keepUnresolvedOptional;
        private final boolean nestedPlaceholder;

        ParsingContext(PropertiesLookup properties, boolean defaultFallbackEnabled, boolean keepUnresolvedOptional,
                       boolean nestedPlaceholder) {
            this.properties = properties;
            this.defaultFallbackEnabled = defaultFallbackEnabled;
            this.keepUnresolvedOptional = keepUnresolvedOptional;
            this.nestedPlaceholder = nestedPlaceholder;
        }

        /**
         * Parses the given input string and replaces all properties
         *
         * @param  input Input string
         * @return       Evaluated string
         */
        public String parse(String input) {
            // does the key turn on or off nested?
            boolean nested = nestedPlaceholder;
            if (input.contains("?nested=true")) {
                nested = true;
                input = input.replace("?nested=true", "");
            } else if (input.contains("?nested=false")) {
                nested = false;
                input = input.replace("?nested=false", "");
            }
            if (nested) {
                return doParseNested(input, new HashSet<>());
            } else {
                return doParse(input);
            }
        }

        /**
         * Parses the given input string and replaces all properties (not nested)
         *
         * @param  input Input string
         * @return       Evaluated string
         */
        private String doParse(String input) {
            if (input == null) {
                return null;
            }

            StringBuilder answer = new StringBuilder();
            Property property;
            while ((property = readProperty(input)) != null) {
                String before = input.substring(0, property.getBeginIndex());
                String after = input.substring(property.getEndIndex());
                String parsed = property.getValue();
                if (parsed != null) {
                    answer.append(before);
                    answer.append(parsed);
                } else if (property.getBeginIndex() == 0 && input.length() == property.getEndIndex()) {
                    // its only a single placeholder which is parsed as null
                    return null;
                }
                input = after;
            }
            if (!input.isEmpty()) {
                answer.append(input);
            }
            return answer.toString();
        }

        /**
         * Recursively parses the given input string and replaces all properties
         *
         * @param  input                Input string
         * @param  replacedPropertyKeys Already replaced property keys used for tracking circular references
         * @return                      Evaluated string
         */
        private String doParseNested(String input, Set<String> replacedPropertyKeys) {
            if (input == null) {
                return null;
            }
            String answer = input;
            Property property;
            while ((property = readProperty(answer)) != null) {
                if (replacedPropertyKeys.contains(property.getKey())) {
                    // Check for circular references (skip optional)
                    boolean optional = property.getKey().startsWith(OPTIONAL_TOKEN);
                    if (optional) {
                        break;
                    } else {
                        throw new IllegalArgumentException(
                                "Circular reference detected with key [" + property.getKey() + "] from text: " + input);
                    }
                }

                Set<String> newReplaced = new HashSet<>(replacedPropertyKeys);
                newReplaced.add(property.getKey());

                int beginIndex = property.getBeginIndex();
                if (beginIndex > 0 && answer.charAt(beginIndex - 1) == '\\') {
                    // The escape character has been escaped, so we need to restore it
                    beginIndex--;
                }
                String before = answer.substring(0, beginIndex);
                String after = answer.substring(property.getEndIndex());
                String parsed = doParseNested(property.getValue(), newReplaced);
                if (parsed != null) {
                    answer = before + parsed + after;
                } else {
                    if (beginIndex == 0 && input.length() == property.getEndIndex()) {
                        // its only a single placeholder which is parsed as null
                        answer = null;
                        break;
                    } else {
                        answer = before + after;
                    }
                }
            }
            return answer;
        }

        /**
         * Finds a property in the given string. It returns {@code null} if there's no property defined.
         *
         * @param  input Input string
         * @return       A property in the given string or {@code null} if not found
         */
        private Property readProperty(String input) {
            // Find the index of the first valid suffix token
            int suffix = getSuffixIndex(input);

            // If not found, ensure that there is no valid prefix token in the string
            if (suffix == -1) {
                if (getMatchingPrefixIndex(input, input.length()) != -1) {
                    throw new IllegalArgumentException(String.format("Missing %s from the text: %s", SUFFIX_TOKEN, input));
                }
                return null;
            }

            // Find the index of the prefix token that matches the suffix token
            int prefix = getMatchingPrefixIndex(input, suffix);
            if (prefix == -1) {
                throw new IllegalArgumentException(String.format("Missing %s from the text: %s", PREFIX_TOKEN, input));
            }

            String key = input.substring(prefix + PREFIX_TOKEN.length(), suffix);
            String value = getPropertyValue(key, input);
            return new Property(prefix, suffix + SUFFIX_TOKEN.length(), key, value);
        }

        /**
         * Gets the first index of the suffix token that is not surrounded by quotes
         *
         * @param  input Input string
         * @return       First index of the suffix token that is not surrounded by quotes
         */
        private int getSuffixIndex(String input) {
            int index = -1;
            do {
                index = input.indexOf(SUFFIX_TOKEN, index + 1);
            } while (index != -1 && (isQuoted(input, index, SUFFIX_TOKEN) || isEscaped(input, index - 1)));
            return index;
        }

        /**
         * Gets the index of the prefix token that matches the suffix at the given index and that is not surrounded by
         * quotes
         *
         * @param  input       Input string
         * @param  suffixIndex Index of the suffix token
         * @return             Index of the prefix token that matches the suffix at the given index and that is not
         *                     surrounded by quotes
         */
        private int getMatchingPrefixIndex(String input, int suffixIndex) {
            int index = suffixIndex;
            do {
                index = input.lastIndexOf(PREFIX_TOKEN, index - 1);
            } while (index != -1 && (isQuoted(input, index, PREFIX_TOKEN) || isEscaped(input, index - 1)));
            return index;
        }

        /**
         * Indicates whether the token at the given index is surrounded by single or double quotes
         *
         * @param  input Input string
         * @param  index Index of the token
         * @param  token Token
         * @return       {@code true}
         */
        private boolean isQuoted(String input, int index, String token) {
            int beforeIndex = index - 1;
            int afterIndex = index + token.length();
            if (beforeIndex >= 0 && afterIndex < input.length()) {
                char before = input.charAt(beforeIndex);
                char after = input.charAt(afterIndex);
                return before == after && (before == '\'' || before == '"');
            }
            return false;
        }

        /**
         * Indicates whether the escape character is at the given index.
         *
         * @param  input Input string
         * @param  index Index where the escape character is checked.
         * @return       {@code true} if the escape character is at the given index, and it is not itself escaped,
         *               {@code false} otherwise.
         */
        private boolean isEscaped(String input, int index) {
            if (index >= 0) {
                return input.charAt(index) == '\\' && (index == 0 || input.charAt(index - 1) != '\\');
            }
            return false;
        }

        /**
         * Gets the value of the property with given key
         *
         * @param  key   Key of the property
         * @param  input Input string (used for exception message if value not found)
         * @return       Value of the property with the given key
         */
        private String getPropertyValue(String key, String input) {
            if (key == null) {
                return null;
            }

            boolean optional = key.startsWith(OPTIONAL_TOKEN);
            if (optional) {
                key = key.substring(OPTIONAL_TOKEN.length());
            }

            // the key may be a function, so lets check this first
            if (propertiesComponent != null) {
                String prefix = StringHelper.before(key, ":");
                PropertiesFunction function = propertiesComponent.getPropertiesFunction(prefix);
                if (function != null) {
                    String remainder = StringHelper.after(key, ":");
                    boolean remainderOptional = remainder.startsWith(OPTIONAL_TOKEN);
                    if (function.lookupFirst(remainder)) {
                        String value = getPropertyValue(remainder, input);
                        if (value == null && (remainderOptional || function.optional(remainder))) {
                            return null;
                        }
                        // it was not possible to resolve
                        if (value != null && value.startsWith(UNRESOLVED_PREFIX_TOKEN)) {
                            return value;
                        } else {
                            remainder = value;
                        }
                    }
                    log.debug("Property with key [{}] is applied by function [{}]", key, function.getName());
                    String value = function.apply(remainder);
                    if (value == null) {
                        if (!remainderOptional) {
                            remainderOptional = function.optional(remainder);
                        }
                        if (!remainderOptional && propertiesComponent != null
                                && propertiesComponent.isIgnoreMissingProperty()) {
                            // property is missing, but we should ignore this and return the placeholder unresolved
                            return UNRESOLVED_PREFIX_TOKEN + key + UNRESOLVED_SUFFIX_TOKEN;
                        }
                        if (!remainderOptional) {
                            throw new IllegalArgumentException(
                                    "Property with key [" + key + "] using function [" + function.getName() + "]"
                                                               + " returned null value which is not allowed, from input: "
                                                               + input);
                        } else {
                            if (keepUnresolvedOptional) {
                                // mark the key as unresolved
                                return UNRESOLVED_PREFIX_TOKEN + OPTIONAL_TOKEN + key + UNRESOLVED_SUFFIX_TOKEN;
                            } else {
                                return null;
                            }
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Property with key [{}] applied by function [{}] -> {}", key, function.getName(),
                                    value);
                        }
                        return value;
                    }
                }
            }

            // they key may have a get or else expression
            String defaultValue = null;
            if (defaultFallbackEnabled && key.contains(GET_OR_ELSE_TOKEN)) {
                defaultValue = StringHelper.after(key, GET_OR_ELSE_TOKEN);
                key = StringHelper.before(key, GET_OR_ELSE_TOKEN);
            }

            String value = doGetPropertyValue(key, defaultValue);
            if (value == null && defaultValue != null) {
                log.debug("Property with key [{}] not found, using default value: {}", key, defaultValue);
                value = defaultValue;
            }

            if (value == null) {
                if (!optional && propertiesComponent != null && propertiesComponent.isIgnoreMissingProperty()) {
                    // property is missing, but we should ignore this and return the placeholder unresolved
                    return UNRESOLVED_PREFIX_TOKEN + key + UNRESOLVED_SUFFIX_TOKEN;
                }
                if (!optional) {
                    StringBuilder esb = new StringBuilder();
                    esb.append("Property with key [").append(key).append("] ");
                    esb.append("not found in properties from text: ").append(input);
                    throw new IllegalArgumentException(esb.toString());
                } else {
                    if (keepUnresolvedOptional) {
                        // mark the key as unresolved
                        return UNRESOLVED_PREFIX_TOKEN + OPTIONAL_TOKEN + key + UNRESOLVED_SUFFIX_TOKEN;
                    } else {
                        return null;
                    }
                }
            }

            return value;
        }

        /**
         * Gets the property with the given key, it returns {@code null} if the property is not found
         *
         * @param  key Key of the property
         * @return     Value of the property or {@code null} if not found
         */
        private String doGetPropertyValue(String key, String defaultValue) {
            if (ObjectHelper.isEmpty(key)) {
                return parseProperty(key, null, properties);
            }

            String value = null;

            // favour local properties if
            Properties local = propertiesComponent != null ? propertiesComponent.getLocalProperties() : null;
            if (local != null) {
                value = local.getProperty(key);
                if (value != null) {
                    String localDefaultValue = null;
                    String loc = location(local, key, "LocalProperties");
                    if (local instanceof OrderedLocationProperties propSource) {
                        Object val = propSource.getDefaultValue(key);
                        if (val != null) {
                            localDefaultValue
                                    = propertiesComponent.getCamelContext().getTypeConverter().tryConvertTo(String.class, val);
                        }
                    }
                    onLookup(key, value, localDefaultValue, loc);
                    log.debug("Found local property: {} with value: {} to be used.", key, value);
                }
            }

            // override is the default mode for ENV
            int envMode = propertiesComponent != null
                    ? propertiesComponent.getEnvironmentVariableMode()
                    : PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_FALLBACK;
            // override is the default mode for SYS
            int sysMode = propertiesComponent != null
                    ? propertiesComponent.getSystemPropertiesMode() : PropertiesComponent.SYSTEM_PROPERTIES_MODE_OVERRIDE;

            if (value == null && envMode == PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_OVERRIDE) {
                value = lookupEnvironmentVariable(key);
                if (value != null) {
                    onLookup(key, value, defaultValue, "ENV");
                    log.debug("Found an OS environment property: {} with value: {} to be used.", key, value);
                }
            }
            if (value == null && sysMode == PropertiesComponent.SYSTEM_PROPERTIES_MODE_OVERRIDE) {
                value = System.getProperty(key);
                if (value != null) {
                    onLookup(key, value, defaultValue, "SYS");
                    log.debug("Found a JVM system property: {} with value: {} to be used.", key, value);
                }
            }

            if (value == null && properties != null) {
                value = properties.lookup(key, defaultValue);
                if (value != null) {
                    log.debug("Found property: {} with value: {} to be used.", key, value);
                }
            }

            if (value == null) {
                // custom lookup in spring boot or other runtimes
                value = customLookup(key);
                if (value != null) {
                    log.debug("Found property (custom lookup): {} with value: {} to be used.", key, value);
                }
            }

            if (value == null && envMode == PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_FALLBACK) {
                value = lookupEnvironmentVariable(key);
                if (value != null) {
                    onLookup(key, value, defaultValue, "ENV");
                    log.debug("Found an OS environment property: {} with value: {} to be used.", key, value);
                }
            }
            if (value == null && sysMode == PropertiesComponent.SYSTEM_PROPERTIES_MODE_FALLBACK) {
                value = System.getProperty(key);
                if (value != null) {
                    onLookup(key, value, defaultValue, "SYS");
                    log.debug("Found a JVM system property: {} with value: {} to be used.", key, value);
                }
            }

            // parse property may return null (such as when using route templates)
            String answer = parseProperty(key, value, properties);
            if (answer == null) {
                answer = value;
            }
            return answer;
        }
    }

    private void onLookup(String name, String value, String defaultValue, String source) {
        for (PropertiesLookupListener listener : propertiesComponent.getPropertiesLookupListeners()) {
            try {
                listener.onLookup(name, value, defaultValue, source);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private static String location(Properties prop, String name, String defaultLocation) {
        String loc = null;
        if (prop instanceof OrderedLocationProperties olp) {
            loc = olp.getLocation(name);
        }
        if (loc == null) {
            loc = defaultLocation;
        }
        return loc;
    }

    /**
     * This inner class is the definition of a property used in a string
     */
    private static final class Property {
        private final int beginIndex;
        private final int endIndex;
        private final String key;
        private final String value;

        private Property(int beginIndex, int endIndex, String key, String value) {
            this.beginIndex = beginIndex;
            this.endIndex = endIndex;
            this.key = key;
            this.value = value;
        }

        /**
         * Gets the beginning index of the property (including the prefix token).
         */
        public int getBeginIndex() {
            return beginIndex;
        }

        /**
         * Gets the ending index of the property (including the suffix token).
         */
        public int getEndIndex() {
            return endIndex;
        }

        /**
         * Gets the key of the property.
         */
        public String getKey() {
            return key;
        }

        /**
         * Gets the value of the property.
         */
        public String getValue() {
            return value;
        }
    }
}
