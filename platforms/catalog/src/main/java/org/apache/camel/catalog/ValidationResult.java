/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.catalog;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Details result of validating endpoint uri.
 */
public class ValidationResult implements Serializable {

    private final String uri;

    // component
    private String syntaxError;
    private String unknownComponent;

    // options
    private Set<String> unknown;
    private Set<String> required;
    private Map<String, String> invalidEnum;
    private Map<String, String[]> invalidEnumChoices;
    private Map<String, String> invalidBoolean;
    private Map<String, String> invalidInteger;
    private Map<String, String> invalidNumber;

    public ValidationResult(String uri) {
        this.uri = uri;
    }

    public boolean isSuccess() {
        return syntaxError == null && unknownComponent == null
                && unknown == null && required == null && invalidEnum == null && invalidEnumChoices == null
                && invalidBoolean == null && invalidInteger == null && invalidNumber == null;
    }

    public void addSyntaxError(String syntaxError) {
        this.syntaxError = syntaxError;
    }

    public void addUnknownComponent(String name) {
        this.unknownComponent = name;
    }

    public void addUnknown(String name) {
        if (unknown == null) {
            unknown = new LinkedHashSet<String>();
        }
        unknown.add(name);
    }

    public void addRequired(String name) {
        if (required == null) {
            required = new LinkedHashSet<String>();
        }
        required.add(name);
    }

    public void addInvalidEnum(String name, String value) {
        if (invalidEnum == null) {
            invalidEnum = new LinkedHashMap<String, String>();
        }
        invalidEnum.put(name, value);
    }

    public void addInvalidEnumChoices(String name, String[] choices) {
        if (invalidEnumChoices == null) {
            invalidEnumChoices = new LinkedHashMap<String, String[]>();
        }
        invalidEnumChoices.put(name, choices);
    }

    public void addInvalidBoolean(String name, String value) {
        if (invalidBoolean == null) {
            invalidBoolean = new LinkedHashMap<String, String>();;
        }
        invalidBoolean.put(name, value);
    }

    public void addInvalidInteger(String name, String value) {
        if (invalidInteger == null) {
            invalidInteger = new LinkedHashMap<String, String>();;
        }
        invalidInteger.put(name, value);
    }

    public void addInvalidNumber(String name, String value) {
        if (invalidNumber == null) {
            invalidNumber = new LinkedHashMap<String, String>();;
        }
        invalidNumber.put(name, value);
    }

    public String getSyntaxError() {
        return syntaxError;
    }

    public Set<String> getUnknown() {
        return unknown;
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

    public Map<String, String> getInvalidBoolean() {
        return invalidBoolean;
    }

    public Map<String, String> getInvalidInteger() {
        return invalidInteger;
    }

    public Map<String, String> getInvalidNumber() {
        return invalidNumber;
    }

    /**
     * A human readable summary of the validation errors.
     *
     * @return the summary, or <tt>null</tt> if no validation errors
     */
    public String summaryErrorMessage() {
        if (isSuccess()) {
            return null;
        }

        if (syntaxError != null) {
            return "Syntax error " + syntaxError;
        } else if (unknownComponent != null) {
            return "Unknown component " + unknownComponent;
        }

        // for each invalid option build a reason message
        Map<String, String> options = new LinkedHashMap<String, String>();
        if (unknown != null) {
            for (String name : unknown) {
                options.put(name, "Unknown field");
            }
        }
        if (required != null) {
            for (String name : required) {
                options.put(name, "Missing required field");
            }
        }
        if (invalidEnum != null) {
            for (Map.Entry<String, String> entry : invalidEnum.entrySet()) {
                String[] choices = invalidEnumChoices.get(entry.getKey());
                String str = Arrays.asList(choices).toString();
                options.put(entry.getKey(), "Invalid enum value: " + entry.getValue() + ". Possible values: " + str);
            }
        }
        if (invalidBoolean != null) {
            for (Map.Entry<String, String> entry : invalidBoolean.entrySet()) {
                options.put(entry.getKey(), "Invalid boolean value: " + entry.getValue());
            }
        }
        if (invalidInteger != null) {
            for (Map.Entry<String, String> entry : invalidInteger.entrySet()) {
                options.put(entry.getKey(), "Invalid integer value: " + entry.getValue());
            }
        }
        if (invalidNumber != null) {
            for (Map.Entry<String, String> entry : invalidNumber.entrySet()) {
                options.put(entry.getKey(), "Invalid number value: " + entry.getValue());
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
        sb.append("Endpoint validator error\n");
        sb.append("---------------------------------------------------------------------------------------------------------------------------------------\n");
        sb.append("\n\t").append(uri).append("\n");
        for (Map.Entry<String, String> option : options.entrySet()) {
            String out = String.format(format, option.getKey(), option.getValue());
            sb.append("\n\t").append(out);
        }
        sb.append("\n\n");

        return sb.toString();
    }
}
