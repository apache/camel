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
package org.apache.camel.catalog;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.camel.catalog.URISupport.isEmpty;

/**
 * Details result of validating endpoint uri.
 */
public class EndpointValidationResult implements Serializable {

    private final String uri;
    private int errors;

    // general
    private String syntaxError;
    private String unknownComponent;
    private String incapable;

    // options
    private Set<String> unknown;
    private Map<String, String[]> unknownSuggestions;
    private Set<String> lenient;
    private Set<String> notConsumerOnly;
    private Set<String> notProducerOnly;
    private Set<String> required;
    private Map<String, String> invalidEnum;
    private Map<String, String[]> invalidEnumChoices;
    private Map<String, String[]> invalidEnumSuggestions;
    private Map<String, String> invalidReference;
    private Map<String, String> invalidBoolean;
    private Map<String, String> invalidInteger;
    private Map<String, String> invalidNumber;
    private Map<String, String> defaultValues;

    public EndpointValidationResult() {
        this(null);
    }

    public EndpointValidationResult(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public boolean hasErrors() {
        return errors > 0;
    }

    public int getNumberOfErrors() {
        return errors;
    }

    public boolean isSuccess() {
        boolean ok = syntaxError == null && unknownComponent == null && incapable == null
                && unknown == null && required == null;
        if (ok) {
            ok = notConsumerOnly == null && notProducerOnly == null;
        }
        if (ok) {
            ok = invalidEnum == null && invalidEnumChoices == null && invalidReference == null
                && invalidBoolean == null && invalidInteger == null && invalidNumber == null;
        }
        return ok;
    }

    public void addSyntaxError(String syntaxError) {
        this.syntaxError = syntaxError;
        errors++;
    }

    public void addIncapable(String uri) {
        this.incapable = uri;
        errors++;
    }

    public void addUnknownComponent(String name) {
        this.unknownComponent = name;
        errors++;
    }

    public void addUnknown(String name) {
        if (unknown == null) {
            unknown = new LinkedHashSet<String>();
        }
        if (!unknown.contains(name)) {
            unknown.add(name);
            errors++;
        }
    }

    public void addUnknownSuggestions(String name, String[] suggestions) {
        if (unknownSuggestions == null) {
            unknownSuggestions = new LinkedHashMap<String, String[]>();
        }
        unknownSuggestions.put(name, suggestions);
    }

    public void addLenient(String name) {
        if (lenient == null) {
            lenient = new LinkedHashSet<String>();
        }
        if (!lenient.contains(name)) {
            lenient.add(name);
        }
    }

    public void addRequired(String name) {
        if (required == null) {
            required = new LinkedHashSet<String>();
        }
        if (!required.contains(name)) {
            required.add(name);
            errors++;
        }
    }

    public void addInvalidEnum(String name, String value) {
        if (invalidEnum == null) {
            invalidEnum = new LinkedHashMap<String, String>();
        }
        if (!invalidEnum.containsKey(name)) {
            invalidEnum.put(name, value);
            errors++;
        }
    }

    public void addInvalidEnumChoices(String name, String[] choices) {
        if (invalidEnumChoices == null) {
            invalidEnumChoices = new LinkedHashMap<String, String[]>();
        }
        invalidEnumChoices.put(name, choices);
    }

    public void addInvalidEnumSuggestions(String name, String[] suggestions) {
        if (invalidEnumSuggestions == null) {
            invalidEnumSuggestions = new LinkedHashMap<String, String[]>();
        }
        invalidEnumSuggestions.put(name, suggestions);
    }

    public void addInvalidReference(String name, String value) {
        if (invalidReference == null) {
            invalidReference = new LinkedHashMap<String, String>();
        }
        if (!invalidReference.containsKey(name)) {
            invalidReference.put(name, value);
            errors++;
        }
    }

    public void addInvalidBoolean(String name, String value) {
        if (invalidBoolean == null) {
            invalidBoolean = new LinkedHashMap<String, String>();
        }
        if (!invalidBoolean.containsKey(name)) {
            invalidBoolean.put(name, value);
            errors++;
        }
    }

    public void addInvalidInteger(String name, String value) {
        if (invalidInteger == null) {
            invalidInteger = new LinkedHashMap<String, String>();
        }
        if (!invalidInteger.containsKey(name)) {
            invalidInteger.put(name, value);
            errors++;
        }
    }

    public void addInvalidNumber(String name, String value) {
        if (invalidNumber == null) {
            invalidNumber = new LinkedHashMap<String, String>();
        }
        if (!invalidNumber.containsKey(name)) {
            invalidNumber.put(name, value);
            errors++;
        }
    }

    public void addDefaultValue(String name, String value)  {
        if (defaultValues == null) {
            defaultValues = new LinkedHashMap<String, String>();
        }
        defaultValues.put(name, value);
    }

    public void addNotConsumerOnly(String name) {
        if (notConsumerOnly == null) {
            notConsumerOnly = new LinkedHashSet<String>();
        }
        if (!notConsumerOnly.contains(name)) {
            notConsumerOnly.add(name);
            errors++;
        }
    }

    public void addNotProducerOnly(String name) {
        if (notProducerOnly == null) {
            notProducerOnly = new LinkedHashSet<String>();
        }
        if (!notProducerOnly.contains(name)) {
            notProducerOnly.add(name);
            errors++;
        }
    }

    public String getSyntaxError() {
        return syntaxError;
    }

    public String getIncapable() {
        return incapable;
    }

    public Set<String> getUnknown() {
        return unknown;
    }

    public Set<String> getLenient() {
        return lenient;
    }

    public Map<String, String[]> getUnknownSuggestions() {
        return unknownSuggestions;
    }

    public String getUnknownComponent() {
        return unknownComponent;
    }

    public Set<String> getRequired() {
        return required;
    }

    public Map<String, String> getInvalidEnum() {
        return invalidEnum;
    }

    public Map<String, String[]> getInvalidEnumChoices() {
        return invalidEnumChoices;
    }

    public List<String> getEnumChoices(String optionName) {
        if (invalidEnumChoices != null) {
            String[] enums = invalidEnumChoices.get(optionName);
            if (enums != null) {
                return Arrays.asList(enums);
            }
        }

        return Collections.emptyList();
    }

    public Map<String, String> getInvalidReference() {
        return invalidReference;
    }

    public Map<String, String> getInvalidBoolean() {
        return invalidBoolean;
    }

    public Map<String, String> getInvalidInteger() {
        return invalidInteger;
    }

    public Map<String, String> getInvalidNumber() {
        return invalidNumber;
    }

    public Map<String, String> getDefaultValues() {
        return defaultValues;
    }

    public Set<String> getNotConsumerOnly() {
        return notConsumerOnly;
    }

    public Set<String> getNotProducerOnly() {
        return notProducerOnly;
    }

    /**
     * A human readable summary of the validation errors.
     *
     * @param includeHeader whether to include a header
     * @return the summary, or <tt>null</tt> if no validation errors
     */
    public String summaryErrorMessage(boolean includeHeader) {
        if (isSuccess()) {
            return null;
        }

        if (incapable != null) {
            return "\tIncapable of parsing uri: " + incapable;
        } else if (syntaxError != null) {
            return "\tSyntax error: " + syntaxError;
        } else if (unknownComponent != null) {
            return "\tUnknown component: " + unknownComponent;
        }

        // for each invalid option build a reason message
        Map<String, String> options = new LinkedHashMap<String, String>();
        if (unknown != null) {
            for (String name : unknown) {
                if (unknownSuggestions != null && unknownSuggestions.containsKey(name)) {
                    String[] suggestions = unknownSuggestions.get(name);
                    if (suggestions != null && suggestions.length > 0) {
                        String str = Arrays.asList(suggestions).toString();
                        options.put(name, "Unknown option. Did you mean: " + str);
                    } else {
                        options.put(name, "Unknown option");
                    }
                } else {
                    options.put(name, "Unknown option");
                }
            }
        }
        if (notConsumerOnly != null) {
            for (String name : notConsumerOnly) {
                options.put(name, "Option not applicable in consumer only mode");
            }
        }
        if (notProducerOnly != null) {
            for (String name : notProducerOnly) {
                options.put(name, "Option not applicable in producer only mode");
            }
        }
        if (required != null) {
            for (String name : required) {
                options.put(name, "Missing required option");
            }
        }
        if (invalidEnum != null) {
            for (Map.Entry<String, String> entry : invalidEnum.entrySet()) {
                String name = entry.getKey();
                String[] choices = invalidEnumChoices.get(name);
                String defaultValue = defaultValues != null ? defaultValues.get(entry.getKey()) : null;
                String str = Arrays.asList(choices).toString();
                String msg = "Invalid enum value: " + entry.getValue() + ". Possible values: " + str;
                if (invalidEnumSuggestions != null) {
                    String[] suggestions = invalidEnumSuggestions.get(name);
                    if (suggestions != null && suggestions.length > 0) {
                        str = Arrays.asList(suggestions).toString();
                        msg += ". Did you mean: " + str;
                    }
                }
                if (defaultValue != null) {
                    msg += ". Default value: " + defaultValue;
                }

                options.put(entry.getKey(), msg);
            }
        }
        if (invalidReference != null) {
            for (Map.Entry<String, String> entry : invalidReference.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty reference value");
                } else if (!entry.getValue().startsWith("#")) {
                    options.put(entry.getKey(), "Invalid reference value: " + entry.getValue() + " must start with #");
                } else {
                    options.put(entry.getKey(), "Invalid reference value: " + entry.getValue());
                }
            }
        }
        if (invalidBoolean != null) {
            for (Map.Entry<String, String> entry : invalidBoolean.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty boolean value");
                } else {
                    options.put(entry.getKey(), "Invalid boolean value: " + entry.getValue());
                }
            }
        }
        if (invalidInteger != null) {
            for (Map.Entry<String, String> entry : invalidInteger.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty integer value");
                } else {
                    options.put(entry.getKey(), "Invalid integer value: " + entry.getValue());
                }
            }
        }
        if (invalidNumber != null) {
            for (Map.Entry<String, String> entry : invalidNumber.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty number value");
                } else {
                    options.put(entry.getKey(), "Invalid number value: " + entry.getValue());
                }
            }
        }

        // build a table with the error summary nicely formatted
        // lets use 24 as min length
        int maxLen = 24;
        for (String key : options.keySet()) {
            maxLen = Math.max(maxLen, key.length());
        }
        String format = "%" + maxLen + "s    %s";

        // build the human error summary
        StringBuilder sb = new StringBuilder();
        if (includeHeader) {
            sb.append("Endpoint validator error\n");
            sb.append("---------------------------------------------------------------------------------------------------------------------------------------\n");
            sb.append("\n");
        }
        if (uri != null) {
            sb.append("\t").append(uri).append("\n");
        } else {
            sb.append("\n");
        }
        for (Map.Entry<String, String> option : options.entrySet()) {
            String out = String.format(format, option.getKey(), option.getValue());
            sb.append("\n\t").append(out);
        }

        return sb.toString();
    }
}
