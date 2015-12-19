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
import java.util.LinkedHashSet;
import java.util.Set;

public class ValidationResult implements Serializable {

    private Set<String> unknown;
    private Set<String> required;
    private Set<String> invalidEnum;
    private Set<String> invalidBoolean;
    private Set<String> invalidInteger;
    private Set<String> invalidNumber;

    public boolean isSuccess() {
        return unknown == null && required == null && invalidEnum == null
                && invalidBoolean == null && invalidInteger == null && invalidNumber == null;
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

    public void addInvalidEnum(String name) {
        if (invalidEnum == null) {
            invalidEnum = new LinkedHashSet<String>();
        }
        invalidEnum.add(name);
    }

    public void addInvalidBoolean(String name) {
        if (invalidBoolean == null) {
            invalidBoolean = new LinkedHashSet<String>();
        }
        invalidBoolean.add(name);
    }

    public void addInvalidInteger(String name) {
        if (invalidInteger == null) {
            invalidInteger = new LinkedHashSet<String>();
        }
        invalidInteger.add(name);
    }

    public void addInvalidNumber(String name) {
        if (invalidNumber == null) {
            invalidNumber = new LinkedHashSet<String>();
        }
        invalidNumber.add(name);
    }

    public Set<String> getUnknown() {
        return unknown;
    }

    public Set<String> getRequired() {
        return required;
    }

    public Set<String> getInvalidEnum() {
        return invalidEnum;
    }

    public Set<String> getInvalidBoolean() {
        return invalidBoolean;
    }

    public Set<String> getInvalidInteger() {
        return invalidInteger;
    }

    public Set<String> getInvalidNumber() {
        return invalidNumber;
    }
}
