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
package org.apache.camel.catalog;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * @since 3.1
 */
abstract class PropertiesValidationResult implements Serializable {

    int errors;
    int warnings;

    // general error
    @Nullable
    String syntaxError;
    // general warnings
    @Nullable
    String unknownComponent;
    @Nullable
    String incapable;

    // options
    @Nullable
    Set<String> unknown;
    @Nullable
    Map<String, String[]> unknownSuggestions;
    @Nullable
    Set<String> required;
    @Nullable
    Set<String> deprecated;
    @Nullable
    Map<String, String> invalidEnum;
    @Nullable
    Map<String, String[]> invalidEnumChoices;
    @Nullable
    Map<String, String[]> invalidEnumSuggestions;
    @Nullable
    Map<String, String> invalidMap;
    @Nullable
    Map<String, String> invalidArray;
    @Nullable
    Map<String, String> invalidReference;
    @Nullable
    Map<String, String> invalidBoolean;
    @Nullable
    Map<String, String> invalidInteger;
    @Nullable
    Map<String, String> invalidNumber;
    @Nullable
    Map<String, String> invalidDuration;
    @Nullable
    Map<String, String> defaultValues;

    /** Returns true if the validation result has one or more errors. */
    public boolean hasErrors() {
        return errors > 0;
    }

    /** Returns the number of validation errors. */
    public int getNumberOfErrors() {
        return errors;
    }

    /** Returns true if the validation result has one or more warnings. */
    public boolean hasWarnings() {
        return warnings > 0;
    }

    /** Returns the number of validation warnings. */
    public int getNumberOfWarnings() {
        return warnings;
    }

    /** Returns true if the validation result has no errors. */
    public boolean isSuccess() {
        boolean ok = syntaxError == null && unknown == null && required == null;
        if (ok) {
            ok = invalidEnum == null && invalidEnumChoices == null && invalidReference == null
                    && invalidBoolean == null && invalidInteger == null && invalidNumber == null
                    && invalidDuration == null;
        }
        if (ok) {
            ok = invalidMap == null && invalidArray == null;
        }
        return ok;
    }

    /** Adds a syntax error to the validation result. */
    public void addSyntaxError(String syntaxError) {
        this.syntaxError = syntaxError;
        errors++;
    }

    /** Adds an incapable URI warning to the validation result. */
    public void addIncapable(String uri) {
        this.incapable = uri;
        warnings++;
    }

    /** Adds an unknown component warning to the validation result. */
    public void addUnknownComponent(String name) {
        this.unknownComponent = name;
        warnings++;
    }

    /** Adds an unknown option error to the validation result. */
    public void addUnknown(String name) {
        if (unknown == null) {
            unknown = new LinkedHashSet<>();
        }
        if (!unknown.contains(name)) {
            unknown.add(name);
            errors++;
        }
    }

    /** Adds spelling suggestions for an unknown option name. */
    public void addUnknownSuggestions(String name, String[] suggestions) {
        if (unknownSuggestions == null) {
            unknownSuggestions = new LinkedHashMap<>();
        }
        unknownSuggestions.put(name, suggestions);
    }

    /** Adds a missing required option error to the validation result. */
    public void addRequired(String name) {
        if (required == null) {
            required = new LinkedHashSet<>();
        }
        if (!required.contains(name)) {
            required.add(name);
            errors++;
        }
    }

    /** Adds a deprecated option warning to the validation result. */
    public void addDeprecated(String name) {
        if (deprecated == null) {
            deprecated = new LinkedHashSet<>();
        }
        deprecated.add(name);
    }

    private String computeErrors(String value) {
        errors++;
        return value;
    }

    /** Adds an invalid enum value error to the validation result. */
    public void addInvalidEnum(String name, String value) {
        if (invalidEnum == null) {
            invalidEnum = new LinkedHashMap<>();
        }

        invalidEnum.computeIfAbsent(name, k -> computeErrors(value));
    }

    /** Adds the valid enum choices for an invalid enum option. */
    public void addInvalidEnumChoices(String name, String[] choices) {
        if (invalidEnumChoices == null) {
            invalidEnumChoices = new LinkedHashMap<>();
        }
        invalidEnumChoices.put(name, choices);
    }

    /** Adds spelling suggestions for an invalid enum option value. */
    public void addInvalidEnumSuggestions(String name, String[] suggestions) {
        if (invalidEnumSuggestions == null) {
            invalidEnumSuggestions = new LinkedHashMap<>();
        }
        invalidEnumSuggestions.put(name, suggestions);
    }

    /** Adds an invalid bean reference error to the validation result. */
    public void addInvalidReference(String name, String value) {
        if (invalidReference == null) {
            invalidReference = new LinkedHashMap<>();
        }

        invalidReference.computeIfAbsent(name, k -> computeErrors(value));
    }

    /** Adds an invalid map value error to the validation result. */
    public void addInvalidMap(String name, String value) {
        if (invalidMap == null) {
            invalidMap = new LinkedHashMap<>();
        }

        invalidMap.computeIfAbsent(name, k -> computeErrors(value));
    }

    /** Adds an invalid array value error to the validation result. */
    public void addInvalidArray(String name, String value) {
        if (invalidArray == null) {
            invalidArray = new LinkedHashMap<>();
        }

        invalidArray.computeIfAbsent(name, k -> computeErrors(value));
    }

    /** Adds an invalid boolean value error to the validation result. */
    public void addInvalidBoolean(String name, String value) {
        if (invalidBoolean == null) {
            invalidBoolean = new LinkedHashMap<>();
        }

        invalidBoolean.computeIfAbsent(name, k -> computeErrors(value));
    }

    /** Adds an invalid integer value error to the validation result. */
    public void addInvalidInteger(String name, String value) {
        if (invalidInteger == null) {
            invalidInteger = new LinkedHashMap<>();
        }

        invalidInteger.computeIfAbsent(name, k -> computeErrors(value));
    }

    /** Adds an invalid number value error to the validation result. */
    public void addInvalidNumber(String name, String value) {
        if (invalidNumber == null) {
            invalidNumber = new LinkedHashMap<>();
        }

        invalidNumber.computeIfAbsent(name, k -> computeErrors(value));
    }

    /** Adds an invalid duration value error to the validation result. */
    public void addInvalidDuration(String name, String value) {
        if (invalidDuration == null) {
            invalidDuration = new LinkedHashMap<>();
        }

        invalidDuration.computeIfAbsent(name, k -> computeErrors(value));
    }

    /** Adds a default value entry for an option that was not configured. */
    public void addDefaultValue(String name, String value) {
        if (defaultValues == null) {
            defaultValues = new LinkedHashMap<>();
        }
        defaultValues.put(name, value);
    }

    /** Returns the syntax error message, or null if there is no syntax error. */
    public @Nullable String getSyntaxError() {
        return syntaxError;
    }

    /** Returns the incapable URI that could not be validated, or null if not applicable. */
    public @Nullable String getIncapable() {
        return incapable;
    }

    /** Returns the set of unknown option names, or null if none. */
    public @Nullable Set<String> getUnknown() {
        return unknown;
    }

    /** Returns spelling suggestions for unknown option names, or null if none. */
    public @Nullable Map<String, String[]> getUnknownSuggestions() {
        return unknownSuggestions;
    }

    /** Returns the name of an unknown component that was used, or null if none. */
    public @Nullable String getUnknownComponent() {
        return unknownComponent;
    }

    /** Returns the set of required option names that are missing, or null if none. */
    public @Nullable Set<String> getRequired() {
        return required;
    }

    /** Returns the set of deprecated option names that are used, or null if none. */
    public @Nullable Set<String> getDeprecated() {
        return deprecated;
    }

    /** Returns options with invalid enum values mapped to the invalid value, or null if none. */
    public @Nullable Map<String, String> getInvalidEnum() {
        return invalidEnum;
    }

    /** Returns options with invalid enum values mapped to the valid choices, or null if none. */
    public @Nullable Map<String, String[]> getInvalidEnumChoices() {
        return invalidEnumChoices;
    }

    /** Returns spelling suggestions for invalid enum option values, or null if none. */
    public @Nullable Map<String, String[]> getInvalidEnumSuggestions() {
        return invalidEnumSuggestions;
    }

    /** Returns the list of valid enum choices for the given option name. */
    public List<String> getEnumChoices(String optionName) {
        if (invalidEnumChoices != null) {
            String[] enums = invalidEnumChoices.get(optionName);
            if (enums != null) {
                return Arrays.asList(enums);
            }
        }

        return Collections.emptyList();
    }

    /** Returns options with invalid bean reference values, or null if none. */
    public @Nullable Map<String, String> getInvalidReference() {
        return invalidReference;
    }

    /** Returns options with invalid map values, or null if none. */
    public @Nullable Map<String, String> getInvalidMap() {
        return invalidMap;
    }

    /** Returns options with invalid array values, or null if none. */
    public @Nullable Map<String, String> getInvalidArray() {
        return invalidArray;
    }

    /** Returns options with invalid boolean values, or null if none. */
    public @Nullable Map<String, String> getInvalidBoolean() {
        return invalidBoolean;
    }

    /** Returns options with invalid integer values, or null if none. */
    public @Nullable Map<String, String> getInvalidInteger() {
        return invalidInteger;
    }

    /** Returns options with invalid number values, or null if none. */
    public @Nullable Map<String, String> getInvalidNumber() {
        return invalidNumber;
    }

    /** Returns options with invalid duration values, or null if none. */
    public @Nullable Map<String, String> getInvalidDuration() {
        return invalidDuration;
    }

    /** Returns options that were not configured and are using default values, or null if none. */
    public @Nullable Map<String, String> getDefaultValues() {
        return defaultValues;
    }

    static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

}
