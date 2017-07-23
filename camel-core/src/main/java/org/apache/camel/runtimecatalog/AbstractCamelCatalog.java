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
package org.apache.camel.runtimecatalog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.camel.runtimecatalog.CatalogHelper.after;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.getNames;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.getPropertyDefaultValue;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.getPropertyEnum;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.getPropertyKind;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.getPropertyNameFromNameWithPrefix;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.getPropertyPrefix;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.getRow;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isComponentConsumerOnly;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isComponentLenientProperties;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isComponentProducerOnly;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isPropertyBoolean;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isPropertyConsumerOnly;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isPropertyInteger;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isPropertyMultiValue;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isPropertyNumber;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isPropertyObject;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isPropertyProducerOnly;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.isPropertyRequired;
import static org.apache.camel.runtimecatalog.JSonSchemaHelper.stripOptionalPrefixFromName;
import static org.apache.camel.runtimecatalog.URISupport.createQueryString;
import static org.apache.camel.runtimecatalog.URISupport.isEmpty;
import static org.apache.camel.runtimecatalog.URISupport.normalizeUri;
import static org.apache.camel.runtimecatalog.URISupport.stripQuery;

/**
 * Base class for both the runtime RuntimeCamelCatalog from camel-core and the complete CamelCatalog from camel-catalog.
 */
public abstract class AbstractCamelCatalog {

    // CHECKSTYLE:OFF

    private static final Pattern SYNTAX_PATTERN = Pattern.compile("(\\w+)");
    private static final Pattern COMPONENT_SYNTAX_PARSER = Pattern.compile("([^\\w-]*)([\\w-]+)");

    private SuggestionStrategy suggestionStrategy;
    private JSonSchemaResolver jsonSchemaResolver;

    public SuggestionStrategy getSuggestionStrategy() {
        return suggestionStrategy;
    }

    public void setSuggestionStrategy(SuggestionStrategy suggestionStrategy) {
        this.suggestionStrategy = suggestionStrategy;
    }

    public JSonSchemaResolver getJSonSchemaResolver() {
        return jsonSchemaResolver;
    }

    public void setJSonSchemaResolver(JSonSchemaResolver resolver) {
        this.jsonSchemaResolver = resolver;
    }

    public boolean validateTimePattern(String pattern) {
        return validateInteger(pattern);
    }

    public EndpointValidationResult validateEndpointProperties(String uri) {
        return validateEndpointProperties(uri, false, false, false);
    }

    public EndpointValidationResult validateEndpointProperties(String uri, boolean ignoreLenientProperties) {
        return validateEndpointProperties(uri, ignoreLenientProperties, false, false);
    }

    public EndpointValidationResult validateProperties(String scheme, Map<String, String> properties) {
        EndpointValidationResult result = new EndpointValidationResult(scheme);

        String json = jsonSchemaResolver.getComponentJSonSchema(scheme);
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        List<Map<String, String>> componentProps = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);

        // endpoint options have higher priority so remove those from component
        // that may clash
        componentProps.stream()
            .filter(c -> rows.stream().noneMatch(e -> Objects.equals(e.get("name"), c.get("name"))))
            .forEach(rows::add);

        boolean lenient = Boolean.getBoolean(properties.getOrDefault("lenient", "false"));

        // the dataformat component refers to a data format so lets add the properties for the selected
        // data format to the list of rows
        if ("dataformat".equals(scheme)) {
            String dfName = properties.get("name");
            if (dfName != null) {
                String dfJson = jsonSchemaResolver.getDataFormatJSonSchema(dfName);
                List<Map<String, String>> dfRows = JSonSchemaHelper.parseJsonSchema("properties", dfJson, true);
                if (dfRows != null && !dfRows.isEmpty()) {
                    rows.addAll(dfRows);
                }
            }
        }

        for (Map.Entry<String, String> property : properties.entrySet()) {
            String value = property.getValue();
            String originalName = property.getKey();
            String name = property.getKey();
            // the name may be using an optional prefix, so lets strip that because the options
            // in the schema are listed without the prefix
            name = stripOptionalPrefixFromName(rows, name);
            // the name may be using a prefix, so lets see if we can find the real property name
            String propertyName = getPropertyNameFromNameWithPrefix(rows, name);
            if (propertyName != null) {
                name = propertyName;
            }

            String prefix = getPropertyPrefix(rows, name);
            String kind = getPropertyKind(rows, name);
            boolean namePlaceholder = name.startsWith("{{") && name.endsWith("}}");
            boolean valuePlaceholder = value.startsWith("{{") || value.startsWith("${") || value.startsWith("$simple{");
            boolean lookup = value.startsWith("#") && value.length() > 1;
            // we cannot evaluate multi values as strict as the others, as we don't know their expected types
            boolean multiValue = prefix != null && originalName.startsWith(prefix) && isPropertyMultiValue(rows, name);

            Map<String, String> row = getRow(rows, name);
            if (row == null) {
                // unknown option

                // only add as error if the component is not lenient properties, or not stub component
                // and the name is not a property placeholder for one or more values
                if (!namePlaceholder && !"stub".equals(scheme)) {
                    if (lenient) {
                        // as if we are lenient then the option is a dynamic extra option which we cannot validate
                        result.addLenient(name);
                    } else {
                        // its unknown
                        result.addUnknown(name);
                        if (suggestionStrategy != null) {
                            String[] suggestions = suggestionStrategy.suggestEndpointOptions(getNames(rows), name);
                            if (suggestions != null) {
                                result.addUnknownSuggestions(name, suggestions);
                            }
                        }
                    }
                }
            } else {
                /* TODO: we may need to add something in the properties to know if they are related to a producer or consumer
                if ("parameter".equals(kind)) {
                    // consumer only or producer only mode for parameters
                    if (consumerOnly) {
                        boolean producer = isPropertyProducerOnly(rows, name);
                        if (producer) {
                            // the option is only for producer so you cannot use it in consumer mode
                            result.addNotConsumerOnly(name);
                        }
                    } else if (producerOnly) {
                        boolean consumer = isPropertyConsumerOnly(rows, name);
                        if (consumer) {
                            // the option is only for consumer so you cannot use it in producer mode
                            result.addNotProducerOnly(name);
                        }
                    }
                }
                */

                // default value
                String defaultValue = getPropertyDefaultValue(rows, name);
                if (defaultValue != null) {
                    result.addDefaultValue(name, defaultValue);
                }

                // is required but the value is empty
                boolean required = isPropertyRequired(rows, name);
                if (required && isEmpty(value)) {
                    result.addRequired(name);
                }

                // is enum but the value is not within the enum range
                // but we can only check if the value is not a placeholder
                String enums = getPropertyEnum(rows, name);
                if (!multiValue && !valuePlaceholder && !lookup && enums != null) {
                    String[] choices = enums.split(",");
                    boolean found = false;
                    for (String s : choices) {
                        if (value.equalsIgnoreCase(s)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        result.addInvalidEnum(name, value);
                        result.addInvalidEnumChoices(name, choices);
                        if (suggestionStrategy != null) {
                            Set<String> names = new LinkedHashSet<>();
                            names.addAll(Arrays.asList(choices));
                            String[] suggestions = suggestionStrategy.suggestEndpointOptions(names, value);
                            if (suggestions != null) {
                                result.addInvalidEnumSuggestions(name, suggestions);
                            }
                        }

                    }
                }

                // is reference lookup of bean (not applicable for @UriPath, enums, or multi-valued)
                if (!multiValue && enums == null && !"path".equals(kind) && isPropertyObject(rows, name)) {
                    // must start with # and be at least 2 characters
                    if (!value.startsWith("#") || value.length() <= 1) {
                        result.addInvalidReference(name, value);
                    }
                }

                // is boolean
                if (!multiValue && !valuePlaceholder && !lookup && isPropertyBoolean(rows, name)) {
                    // value must be a boolean
                    boolean bool = "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                    if (!bool) {
                        result.addInvalidBoolean(name, value);
                    }
                }

                // is integer
                if (!multiValue && !valuePlaceholder && !lookup && isPropertyInteger(rows, name)) {
                    // value must be an integer
                    boolean valid = validateInteger(value);
                    if (!valid) {
                        result.addInvalidInteger(name, value);
                    }
                }

                // is number
                if (!multiValue && !valuePlaceholder && !lookup && isPropertyNumber(rows, name)) {
                    // value must be an number
                    boolean valid = false;
                    try {
                        valid = !Double.valueOf(value).isNaN() || !Float.valueOf(value).isNaN();
                    } catch (Exception e) {
                        // ignore
                    }
                    if (!valid) {
                        result.addInvalidNumber(name, value);
                    }
                }
            }
        }

        // now check if all required values are there, and that a default value does not exists
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            boolean required = isPropertyRequired(rows, name);
            if (required) {
                String value = properties.get(name);
                if (isEmpty(value)) {
                    value = getPropertyDefaultValue(rows, name);
                }
                if (isEmpty(value)) {
                    result.addRequired(name);
                }
            }
        }

        return result;
    }

    public EndpointValidationResult validateEndpointProperties(String uri, boolean ignoreLenientProperties, boolean consumerOnly, boolean producerOnly) {
        EndpointValidationResult result = new EndpointValidationResult(uri);

        Map<String, String> properties;
        List<Map<String, String>> rows;
        boolean lenientProperties;
        String scheme;

        try {
            String json = null;

            // parse the uri
            URI u = normalizeUri(uri);
            scheme = u.getScheme();

            if (scheme != null) {
                json = jsonSchemaResolver.getComponentJSonSchema(scheme);
            }
            if (json == null) {
                // if the uri starts with a placeholder then we are also incapable of parsing it as we wasn't able to resolve the component name
                if (uri.startsWith("{{")) {
                    result.addIncapable(uri);
                } else if (scheme != null) {
                    result.addUnknownComponent(scheme);
                } else {
                    result.addUnknownComponent(uri);
                }
                return result;
            }

            rows = JSonSchemaHelper.parseJsonSchema("component", json, false);

            // is the component capable of both consumer and producer?
            boolean canConsumeAndProduce = false;
            if (!isComponentConsumerOnly(rows) && !isComponentProducerOnly(rows)) {
                canConsumeAndProduce = true;
            }

            if (canConsumeAndProduce && consumerOnly) {
                // lenient properties is not support in consumer only mode if the component can do both of them
                lenientProperties = false;
            } else {
                // only enable lenient properties if we should not ignore
                lenientProperties = !ignoreLenientProperties && isComponentLenientProperties(rows);
            }
            rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
            properties = endpointProperties(uri);
        } catch (URISyntaxException e) {
            if (uri.startsWith("{{")) {
                // if the uri starts with a placeholder then we are also incapable of parsing it as we wasn't able to resolve the component name
                result.addIncapable(uri);
            } else {
                result.addSyntaxError(e.getMessage());
            }

            return result;
        }

        // the dataformat component refers to a data format so lets add the properties for the selected
        // data format to the list of rows
        if ("dataformat".equals(scheme)) {
            String dfName = properties.get("name");
            if (dfName != null) {
                String dfJson = jsonSchemaResolver.getDataFormatJSonSchema(dfName);
                List<Map<String, String>> dfRows = JSonSchemaHelper.parseJsonSchema("properties", dfJson, true);
                if (dfRows != null && !dfRows.isEmpty()) {
                    rows.addAll(dfRows);
                }
            }
        }

        for (Map.Entry<String, String> property : properties.entrySet()) {
            String value = property.getValue();
            String originalName = property.getKey();
            String name = property.getKey();
            // the name may be using an optional prefix, so lets strip that because the options
            // in the schema are listed without the prefix
            name = stripOptionalPrefixFromName(rows, name);
            // the name may be using a prefix, so lets see if we can find the real property name
            String propertyName = getPropertyNameFromNameWithPrefix(rows, name);
            if (propertyName != null) {
                name = propertyName;
            }

            String prefix = getPropertyPrefix(rows, name);
            String kind = getPropertyKind(rows, name);
            boolean namePlaceholder = name.startsWith("{{") && name.endsWith("}}");
            boolean valuePlaceholder = value.startsWith("{{") || value.startsWith("${") || value.startsWith("$simple{");
            boolean lookup = value.startsWith("#") && value.length() > 1;
            // we cannot evaluate multi values as strict as the others, as we don't know their expected types
            boolean mulitValue = prefix != null && originalName.startsWith(prefix) && isPropertyMultiValue(rows, name);

            Map<String, String> row = getRow(rows, name);
            if (row == null) {
                // unknown option

                // only add as error if the component is not lenient properties, or not stub component
                // and the name is not a property placeholder for one or more values
                if (!namePlaceholder && !"stub".equals(scheme)) {
                    if (lenientProperties) {
                        // as if we are lenient then the option is a dynamic extra option which we cannot validate
                        result.addLenient(name);
                    } else {
                        // its unknown
                        result.addUnknown(name);
                        if (suggestionStrategy != null) {
                            String[] suggestions = suggestionStrategy.suggestEndpointOptions(getNames(rows), name);
                            if (suggestions != null) {
                                result.addUnknownSuggestions(name, suggestions);
                            }
                        }
                    }
                }
            } else {
                if ("parameter".equals(kind)) {
                    // consumer only or producer only mode for parameters
                    if (consumerOnly) {
                        boolean producer = isPropertyProducerOnly(rows, name);
                        if (producer) {
                            // the option is only for producer so you cannot use it in consumer mode
                            result.addNotConsumerOnly(name);
                        }
                    } else if (producerOnly) {
                        boolean consumer = isPropertyConsumerOnly(rows, name);
                        if (consumer) {
                            // the option is only for consumer so you cannot use it in producer mode
                            result.addNotProducerOnly(name);
                        }
                    }
                }

                // default value
                String defaultValue = getPropertyDefaultValue(rows, name);
                if (defaultValue != null) {
                    result.addDefaultValue(name, defaultValue);
                }

                // is required but the value is empty
                boolean required = isPropertyRequired(rows, name);
                if (required && isEmpty(value)) {
                    result.addRequired(name);
                }

                // is enum but the value is not within the enum range
                // but we can only check if the value is not a placeholder
                String enums = getPropertyEnum(rows, name);
                if (!mulitValue && !valuePlaceholder && !lookup && enums != null) {
                    String[] choices = enums.split(",");
                    boolean found = false;
                    for (String s : choices) {
                        if (value.equalsIgnoreCase(s)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        result.addInvalidEnum(name, value);
                        result.addInvalidEnumChoices(name, choices);
                        if (suggestionStrategy != null) {
                            Set<String> names = new LinkedHashSet<>();
                            names.addAll(Arrays.asList(choices));
                            String[] suggestions = suggestionStrategy.suggestEndpointOptions(names, value);
                            if (suggestions != null) {
                                result.addInvalidEnumSuggestions(name, suggestions);
                            }
                        }

                    }
                }

                // is reference lookup of bean (not applicable for @UriPath, enums, or multi-valued)
                if (!mulitValue && enums == null && !"path".equals(kind) && isPropertyObject(rows, name)) {
                    // must start with # and be at least 2 characters
                    if (!value.startsWith("#") || value.length() <= 1) {
                        result.addInvalidReference(name, value);
                    }
                }

                // is boolean
                if (!mulitValue && !valuePlaceholder && !lookup && isPropertyBoolean(rows, name)) {
                    // value must be a boolean
                    boolean bool = "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                    if (!bool) {
                        result.addInvalidBoolean(name, value);
                    }
                }

                // is integer
                if (!mulitValue && !valuePlaceholder && !lookup && isPropertyInteger(rows, name)) {
                    // value must be an integer
                    boolean valid = validateInteger(value);
                    if (!valid) {
                        result.addInvalidInteger(name, value);
                    }
                }

                // is number
                if (!mulitValue && !valuePlaceholder && !lookup && isPropertyNumber(rows, name)) {
                    // value must be an number
                    boolean valid = false;
                    try {
                        valid = !Double.valueOf(value).isNaN() || !Float.valueOf(value).isNaN();
                    } catch (Exception e) {
                        // ignore
                    }
                    if (!valid) {
                        result.addInvalidNumber(name, value);
                    }
                }
            }
        }

        // now check if all required values are there, and that a default value does not exists
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            boolean required = isPropertyRequired(rows, name);
            if (required) {
                String value = properties.get(name);
                if (isEmpty(value)) {
                    value = getPropertyDefaultValue(rows, name);
                }
                if (isEmpty(value)) {
                    result.addRequired(name);
                }
            }
        }

        return result;
    }

    public Map<String, String> endpointProperties(String uri) throws URISyntaxException {
        // need to normalize uri first
        URI u = normalizeUri(uri);
        String scheme = u.getScheme();

        String json = jsonSchemaResolver.getComponentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Cannot find endpoint with scheme " + scheme);
        }

        // grab the syntax
        String syntax = null;
        String alternativeSyntax = null;
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : rows) {
            if (row.containsKey("syntax")) {
                syntax = row.get("syntax");
            }
            if (row.containsKey("alternativeSyntax")) {
                alternativeSyntax = row.get("alternativeSyntax");
            }
        }
        if (syntax == null) {
            throw new IllegalArgumentException("Endpoint with scheme " + scheme + " has no syntax defined in the json schema");
        }

        // only if we support alternative syntax, and the uri contains the username and password in the authority
        // part of the uri, then we would need some special logic to capture that information and strip those
        // details from the uri, so we can continue parsing the uri using the normal syntax
        Map<String, String> userInfoOptions = new LinkedHashMap<String, String>();
        if (alternativeSyntax != null && alternativeSyntax.contains("@")) {
            // clip the scheme from the syntax
            alternativeSyntax = after(alternativeSyntax, ":");
            // trim so only userinfo
            int idx = alternativeSyntax.indexOf("@");
            String fields = alternativeSyntax.substring(0, idx);
            String[] names = fields.split(":");

            // grab authority part and grab username and/or password
            String authority = u.getAuthority();
            if (authority != null && authority.contains("@")) {
                String username = null;
                String password = null;

                // grab unserinfo part before @
                String userInfo = authority.substring(0, authority.indexOf("@"));
                String[] parts = userInfo.split(":");
                if (parts.length == 2) {
                    username = parts[0];
                    password = parts[1];
                } else {
                    // only username
                    username = userInfo;
                }

                // remember the username and/or password which we add later to the options
                if (names.length == 2) {
                    userInfoOptions.put(names[0], username);
                    if (password != null) {
                        // password is optional
                        userInfoOptions.put(names[1], password);
                    }
                }
            }
        }

        // clip the scheme from the syntax
        syntax = after(syntax, ":");
        // clip the scheme from the uri
        uri = after(uri, ":");
        String uriPath = stripQuery(uri);

        // strip user info from uri path
        if (!userInfoOptions.isEmpty()) {
            int idx = uriPath.indexOf('@');
            if (idx > -1) {
                uriPath = uriPath.substring(idx + 1);
            }
        }

        // strip double slash in the start
        if (uriPath != null && uriPath.startsWith("//")) {
            uriPath = uriPath.substring(2);
        }

        // parse the syntax and find the names of each option
        Matcher matcher = SYNTAX_PATTERN.matcher(syntax);
        List<String> word = new ArrayList<String>();
        while (matcher.find()) {
            String s = matcher.group(1);
            if (!scheme.equals(s)) {
                word.add(s);
            }
        }
        // parse the syntax and find each token between each option
        String[] tokens = SYNTAX_PATTERN.split(syntax);

        // find the position where each option start/end
        List<String> word2 = new ArrayList<String>();
        int prev = 0;
        int prevPath = 0;

        // special for activemq/jms where the enum for destinationType causes a token issue as it includes a colon
        // for 'temp:queue' and 'temp:topic' values
        if ("activemq".equals(scheme) || "jms".equals(scheme)) {
            if (uriPath.startsWith("temp:")) {
                prevPath = 5;
            }
        }

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }

            // special for some tokens where :// can be used also, eg http://foo
            int idx = -1;
            int len = 0;
            if (":".equals(token)) {
                idx = uriPath.indexOf("://", prevPath);
                len = 3;
            }
            if (idx == -1) {
                idx = uriPath.indexOf(token, prevPath);
                len = token.length();
            }

            if (idx > 0) {
                String option = uriPath.substring(prev, idx);
                word2.add(option);
                prev = idx + len;
                prevPath = prev;
            }
        }
        // special for last or if we did not add anyone
        if (prev > 0 || word2.isEmpty()) {
            String option = uriPath.substring(prev);
            word2.add(option);
        }

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);

        boolean defaultValueAdded = false;

        // now parse the uri to know which part isw what
        Map<String, String> options = new LinkedHashMap<String, String>();

        // include the username and password from the userinfo section
        if (!userInfoOptions.isEmpty()) {
            options.putAll(userInfoOptions);
        }

        // word contains the syntax path elements
        Iterator<String> it = word2.iterator();
        for (int i = 0; i < word.size(); i++) {
            String key = word.get(i);

            boolean allOptions = word.size() == word2.size();
            boolean required = isPropertyRequired(rows, key);
            String defaultValue = getPropertyDefaultValue(rows, key);

            // we have all options so no problem
            if (allOptions) {
                String value = it.next();
                options.put(key, value);
            } else {
                // we have a little problem as we do not not have all options
                if (!required) {
                    String value = null;

                    boolean last = i == word.size() - 1;
                    if (last) {
                        // if its the last value then use it instead of the default value
                        value = it.hasNext() ? it.next() : null;
                        if (value != null) {
                            options.put(key, value);
                        } else {
                            value = defaultValue;
                        }
                    }
                    if (value != null) {
                        options.put(key, value);
                        defaultValueAdded = true;
                    }
                } else {
                    String value = it.hasNext() ? it.next() : null;
                    if (value != null) {
                        options.put(key, value);
                    }
                }
            }
        }

        Map<String, String> answer = new LinkedHashMap<String, String>();

        // remove all options which are using default values and are not required
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (defaultValueAdded) {
                boolean required = isPropertyRequired(rows, key);
                String defaultValue = getPropertyDefaultValue(rows, key);

                if (!required && defaultValue != null) {
                    if (defaultValue.equals(value)) {
                        continue;
                    }
                }
            }

            // we should keep this in the answer
            answer.put(key, value);
        }

        // now parse the uri parameters
        Map<String, Object> parameters = URISupport.parseParameters(u);

        // and covert the values to String so its JMX friendly
        while (!parameters.isEmpty()) {
            Map.Entry<String, Object> entry = parameters.entrySet().iterator().next();
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().toString() : "";

            boolean multiValued = isPropertyMultiValue(rows, key);
            if (multiValued) {
                String prefix = getPropertyPrefix(rows, key);
                // extra all the multi valued options
                Map<String, Object> values = URISupport.extractProperties(parameters, prefix);
                // build a string with the extra multi valued options with the prefix and & as separator
                CollectionStringBuffer csb = new CollectionStringBuffer("&");
                for (Map.Entry<String, Object> multi : values.entrySet()) {
                    String line = prefix + multi.getKey() + "=" + (multi.getValue() != null ? multi.getValue().toString() : "");
                    csb.append(line);
                }
                // append the extra multi-values to the existing (which contains the first multi value)
                if (!csb.isEmpty()) {
                    value = value + "&" + csb.toString();
                }
            }

            answer.put(key, value);
            // remove the parameter as we run in a while loop until no more parameters
            parameters.remove(key);
        }

        return answer;
    }

    public Map<String, String> endpointLenientProperties(String uri) throws URISyntaxException {
        // need to normalize uri first

        // parse the uri
        URI u = normalizeUri(uri);
        String scheme = u.getScheme();

        String json = jsonSchemaResolver.getComponentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Cannot find endpoint with scheme " + scheme);
        }

        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);

        // now parse the uri parameters
        Map<String, Object> parameters = URISupport.parseParameters(u);

        // all the known options
        Set<String> names = getNames(rows);

        Map<String, String> answer = new LinkedHashMap<>();

        // and covert the values to String so its JMX friendly
        parameters.forEach((k, v) -> {
            String key = k;
            String value = v != null ? v.toString() : "";

            // is the key a prefix property
            int dot = key.indexOf('.');
            if (dot != -1) {
                String prefix = key.substring(0, dot + 1); // include dot in prefix
                String option = getPropertyNameFromNameWithPrefix(rows, prefix);
                if (option == null || !isPropertyMultiValue(rows, option)) {
                    answer.put(key, value);
                }
            } else if (!names.contains(key)) {
                answer.put(key, value);
            }
        });

        return answer;
    }

    public String endpointComponentName(String uri) {
        if (uri != null) {
            int idx = uri.indexOf(":");
            if (idx > 0) {
                return uri.substring(0, idx);
            }
        }
        return null;
    }

    public String asEndpointUri(String scheme, String json, boolean encode) throws URISyntaxException {
        return doAsEndpointUri(scheme, json, "&", encode);
    }

    public String asEndpointUriXml(String scheme, String json, boolean encode) throws URISyntaxException {
        return doAsEndpointUri(scheme, json, "&amp;", encode);
    }

    private String doAsEndpointUri(String scheme, String json, String ampersand, boolean encode) throws URISyntaxException {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);

        Map<String, String> copy = new HashMap<String, String>();
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            String required = row.get("required");
            String value = row.get("value");
            String defaultValue = row.get("defaultValue");

            // only add if either required, or the value is != default value
            String valueToAdd = null;
            if ("true".equals(required)) {
                valueToAdd = value != null ? value : defaultValue;
                if (valueToAdd == null) {
                    valueToAdd = "";
                }
            } else {
                // if we have a value and no default then add it
                if (value != null && defaultValue == null) {
                    valueToAdd = value;
                }
                // otherwise only add if the value is != default value
                if (value != null && defaultValue != null && !value.equals(defaultValue)) {
                    valueToAdd = value;
                }
            }

            if (valueToAdd != null) {
                copy.put(name, valueToAdd);
            }
        }

        return doAsEndpointUri(scheme, copy, ampersand, encode);
    }

    public String asEndpointUri(String scheme, Map<String, String> properties, boolean encode) throws URISyntaxException {
        return doAsEndpointUri(scheme, properties, "&", encode);
    }

    public String asEndpointUriXml(String scheme, Map<String, String> properties, boolean encode) throws URISyntaxException {
        return doAsEndpointUri(scheme, properties, "&amp;", encode);
    }

    String doAsEndpointUri(String scheme, Map<String, String> properties, String ampersand, boolean encode) throws URISyntaxException {
        String json = jsonSchemaResolver.getComponentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Cannot find endpoint with scheme " + scheme);
        }

        // grab the syntax
        String originalSyntax = null;
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : rows) {
            if (row.containsKey("syntax")) {
                originalSyntax = row.get("syntax");
                break;
            }
        }
        if (originalSyntax == null) {
            throw new IllegalArgumentException("Endpoint with scheme " + scheme + " has no syntax defined in the json schema");
        }

        // do any properties filtering which can be needed for some special components
        properties = filterProperties(scheme, properties);

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);

        // clip the scheme from the syntax
        String syntax = "";
        if (originalSyntax.contains(":")) {
            originalSyntax = after(originalSyntax, ":");
        }

        // build at first according to syntax (use a tree map as we want the uri options sorted)
        Map<String, String> copy = new TreeMap<>(properties);
        Matcher syntaxMatcher = COMPONENT_SYNTAX_PARSER.matcher(originalSyntax);
        while (syntaxMatcher.find()) {
            syntax += syntaxMatcher.group(1);
            String propertyName = syntaxMatcher.group(2);
            String propertyValue = copy.remove(propertyName);
            syntax += propertyValue != null ? propertyValue : propertyName;
        }

        // do we have all the options the original syntax needs (easy way)
        String[] keys = syntaxKeys(originalSyntax);
        boolean hasAllKeys = properties.keySet().containsAll(Arrays.asList(keys));

        // build endpoint uri
        StringBuilder sb = new StringBuilder();
        // add scheme later as we need to take care if there is any context-path or query parameters which
        // affect how the URI should be constructed

        if (hasAllKeys) {
            // we have all the keys for the syntax so we can build the uri the easy way
            sb.append(syntax);

            if (!copy.isEmpty()) {
                boolean hasQuestionmark = sb.toString().contains("?");
                // the last option may already contain a ? char, if so we should use & instead of ?
                sb.append(hasQuestionmark ? ampersand : '?');
                String query = createQueryString(copy, ampersand, encode);
                sb.append(query);
            }
        } else {
            // TODO: revisit this and see if we can do this in another way
            // oh darn some options is missing, so we need a complex way of building the uri

            // the tokens between the options in the path
            String[] tokens = syntax.split("\\w+");

            // parse the syntax into each options
            Matcher matcher = SYNTAX_PATTERN.matcher(originalSyntax);
            List<String> options = new ArrayList<String>();
            while (matcher.find()) {
                String s = matcher.group(1);
                options.add(s);
            }

            // need to preserve {{ and }} from the syntax
            // (we need to use words only as its provisional placeholders)
            syntax = syntax.replaceAll("\\{\\{", "BEGINCAMELPLACEHOLDER");
            syntax = syntax.replaceAll("\\}\\}", "ENDCAMELPLACEHOLDER");

            // parse the syntax into each options
            Matcher matcher2 = SYNTAX_PATTERN.matcher(syntax);
            List<String> options2 = new ArrayList<String>();
            while (matcher2.find()) {
                String s = matcher2.group(1);
                s = s.replaceAll("BEGINCAMELPLACEHOLDER", "\\{\\{");
                s = s.replaceAll("ENDCAMELPLACEHOLDER", "\\}\\}");
                options2.add(s);
            }

            // build the endpoint
            int range = 0;
            boolean first = true;
            boolean hasQuestionmark = false;
            for (int i = 0; i < options.size(); i++) {
                String key = options.get(i);
                String key2 = options2.get(i);
                String token = null;
                if (tokens.length > i) {
                    token = tokens[i];
                }

                boolean contains = properties.containsKey(key);
                if (!contains) {
                    // if the key are similar we have no explicit value and can try to find a default value if the option is required
                    if (isPropertyRequired(rows, key)) {
                        String value = getPropertyDefaultValue(rows, key);
                        if (value != null) {
                            properties.put(key, value);
                            key2 = value;
                        }
                    }
                }

                // was the option provided?
                if (properties.containsKey(key)) {
                    if (!first && token != null) {
                        sb.append(token);
                    }
                    hasQuestionmark |= key.contains("?") || (token != null && token.contains("?"));
                    sb.append(key2);
                    first = false;
                }
                range++;
            }
            // append any extra options that was in surplus for the last
            while (range < options2.size()) {
                String token = null;
                if (tokens.length > range) {
                    token = tokens[range];
                }
                String key2 = options2.get(range);
                sb.append(token);
                sb.append(key2);
                hasQuestionmark |= key2.contains("?") || (token != null && token.contains("?"));
                range++;
            }


            if (!copy.isEmpty()) {
                // the last option may already contain a ? char, if so we should use & instead of ?
                sb.append(hasQuestionmark ? ampersand : '?');
                String query = createQueryString(copy, ampersand, encode);
                sb.append(query);
            }
        }

        String remainder = sb.toString();
        boolean queryOnly = remainder.startsWith("?");
        if (queryOnly) {
            // it has only query parameters
            return scheme + remainder;
        } else if (!remainder.isEmpty()) {
            // it has context path and possible query parameters
            return scheme + ":" + remainder;
        } else {
            // its empty without anything
            return scheme;
        }
    }

    @Deprecated
    private static String[] syntaxTokens(String syntax) {
        // build tokens between the words
        List<String> tokens = new ArrayList<>();
        // preserve backwards behavior which had an empty token first
        tokens.add("");

        String current = "";
        for (int i = 0; i < syntax.length(); i++) {
            char ch = syntax.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                // reset for new current tokens
                if (current.length() > 0) {
                    tokens.add(current);
                    current = "";
                }
            } else {
                current += ch;
            }
        }
        // anything left over?
        if (current.length() > 0) {
            tokens.add(current);
        }

        return tokens.toArray(new String[tokens.size()]);
    }

    private static String[] syntaxKeys(String syntax) {
        // build tokens between the separators
        List<String> tokens = new ArrayList<>();

        if (syntax != null) {
            String current = "";
            for (int i = 0; i < syntax.length(); i++) {
                char ch = syntax.charAt(i);
                if (Character.isLetterOrDigit(ch)) {
                    current += ch;
                } else {
                    // reset for new current tokens
                    if (current.length() > 0) {
                        tokens.add(current);
                        current = "";
                    }
                }
            }
            // anything left over?
            if (current.length() > 0) {
                tokens.add(current);
            }
        }

        return tokens.toArray(new String[tokens.size()]);
    }

    public SimpleValidationResult validateSimpleExpression(String simple) {
        return doValidateSimple(null, simple, false);
    }

    public SimpleValidationResult validateSimpleExpression(ClassLoader classLoader, String simple) {
        return doValidateSimple(classLoader, simple, false);
    }

    public SimpleValidationResult validateSimplePredicate(String simple) {
        return doValidateSimple(null, simple, true);
    }

    public SimpleValidationResult validateSimplePredicate(ClassLoader classLoader, String simple) {
        return doValidateSimple(classLoader, simple, true);
    }

    private SimpleValidationResult doValidateSimple(ClassLoader classLoader, String simple, boolean predicate) {
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }

        // if there are {{ }}} property placeholders then we need to resolve them to something else
        // as the simple parse cannot resolve them before parsing as we dont run the actual Camel application
        // with property placeholders setup so we need to dummy this by replace the {{ }} to something else
        // therefore we use an more unlikely character: {{XXX}} to ~^XXX^~
        String resolved = simple.replaceAll("\\{\\{(.+)\\}\\}", "~^$1^~");

        SimpleValidationResult answer = new SimpleValidationResult(simple);

        Object instance = null;
        Class clazz = null;
        try {
            clazz = classLoader.loadClass("org.apache.camel.language.simple.SimpleLanguage");
            instance = clazz.newInstance();
        } catch (Exception e) {
            // ignore
        }

        if (clazz != null && instance != null) {
            Throwable cause = null;
            try {
                if (predicate) {
                    instance.getClass().getMethod("createPredicate", String.class).invoke(instance, resolved);
                } else {
                    instance.getClass().getMethod("createExpression", String.class).invoke(instance, resolved);
                }
            } catch (InvocationTargetException e) {
                cause = e.getTargetException();
            } catch (Exception e) {
                cause = e;
            }

            if (cause != null) {

                // reverse ~^XXX^~ back to {{XXX}}
                String errMsg = cause.getMessage();
                errMsg = errMsg.replaceAll("\\~\\^(.+)\\^\\~", "{{$1}}");

                answer.setError(errMsg);

                // is it simple parser exception then we can grab the index where the problem is
                if (cause.getClass().getName().equals("org.apache.camel.language.simple.types.SimpleIllegalSyntaxException")
                    || cause.getClass().getName().equals("org.apache.camel.language.simple.types.SimpleParserException")) {
                    try {
                        // we need to grab the index field from those simple parser exceptions
                        Method method = cause.getClass().getMethod("getIndex");
                        Object result = method.invoke(cause);
                        if (result != null) {
                            int index = (int) result;
                            answer.setIndex(index);
                        }
                    } catch (Throwable i) {
                        // ignore
                    }
                }

                // we need to grab the short message field from this simple syntax exception
                if (cause.getClass().getName().equals("org.apache.camel.language.simple.types.SimpleIllegalSyntaxException")) {
                    try {
                        Method method = cause.getClass().getMethod("getShortMessage");
                        Object result = method.invoke(cause);
                        if (result != null) {
                            String msg = (String) result;
                            answer.setShortError(msg);
                        }
                    } catch (Throwable i) {
                        // ignore
                    }

                    if (answer.getShortError() == null) {
                        // fallback and try to make existing message short instead
                        String msg = answer.getError();
                        // grab everything before " at location " which would be regarded as the short message
                        int idx = msg.indexOf(" at location ");
                        if (idx > 0) {
                            msg = msg.substring(0, idx);
                            answer.setShortError(msg);
                        }
                    }
                }
            }
        }

        return answer;
    }

    public LanguageValidationResult validateLanguagePredicate(ClassLoader classLoader, String language, String text) {
        return doValidateLanguage(classLoader, language, text, true);
    }

    public LanguageValidationResult validateLanguageExpression(ClassLoader classLoader, String language, String text) {
        return doValidateLanguage(classLoader, language, text, false);
    }

    private LanguageValidationResult doValidateLanguage(ClassLoader classLoader, String language, String text, boolean predicate) {
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }

        SimpleValidationResult answer = new SimpleValidationResult(text);

        String json = jsonSchemaResolver.getLanguageJSonSchema(language);
        if (json == null) {
            answer.setError("Unknown language " + language);
            return answer;
        }

        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("language", json, false);
        String className = null;
        for (Map<String, String> row : rows) {
            if (row.containsKey("javaType")) {
                className = row.get("javaType");
            }
        }

        if (className == null) {
            answer.setError("Cannot find javaType for language " + language);
            return answer;
        }

        Object instance = null;
        Class clazz = null;
        try {
            clazz = classLoader.loadClass(className);
            instance = clazz.newInstance();
        } catch (Exception e) {
            // ignore
        }

        if (clazz != null && instance != null) {
            Throwable cause = null;
            try {
                if (predicate) {
                    instance.getClass().getMethod("createPredicate", String.class).invoke(instance, text);
                } else {
                    instance.getClass().getMethod("createExpression", String.class).invoke(instance, text);
                }
            } catch (InvocationTargetException e) {
                cause = e.getTargetException();
            } catch (Exception e) {
                cause = e;
            }

            if (cause != null) {
                answer.setError(cause.getMessage());
            }
        }

        return answer;
    }

    /**
     * Special logic for log endpoints to deal when showAll=true
     */
    private Map<String, String> filterProperties(String scheme, Map<String, String> options) {
        if ("log".equals(scheme)) {
            String showAll = options.get("showAll");
            if ("true".equals(showAll)) {
                Map<String, String> filtered = new LinkedHashMap<String, String>();
                // remove all the other showXXX options when showAll=true
                for (Map.Entry<String, String> entry : options.entrySet()) {
                    String key = entry.getKey();
                    boolean skip = key.startsWith("show") && !key.equals("showAll");
                    if (!skip) {
                        filtered.put(key, entry.getValue());
                    }
                }
                return filtered;
            }
        }
        // use as-is
        return options;
    }

    private static boolean validateInteger(String value) {
        boolean valid = false;
        try {
            valid = Integer.valueOf(value) != null;
        } catch (Exception e) {
            // ignore
        }
        if (!valid) {
            // it may be a time pattern, such as 5s for 5 seconds = 5000
            try {
                TimePatternConverter.toMilliSeconds(value);
                valid = true;
            } catch (Exception e) {
                // ignore
            }
        }
        return valid;
    }

    // CHECKSTYLE:ON

}
