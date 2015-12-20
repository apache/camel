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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ValidationResult implements Serializable {

    // component
    private String syntaxError;
    private String unknownComponent;

    // options
    private Set<String> unknown;
    private Set<String> required;
    private Map<String, String> invalidEnum;
    private Map<String, String> invalidBoolean;
    private Map<String, String> invalidInteger;
    private Map<String, String> invalidNumber;

    public boolean isSuccess() {
        return syntaxError == null && unknownComponent == null
                && unknown == null && required == null && invalidEnum == null
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
}
