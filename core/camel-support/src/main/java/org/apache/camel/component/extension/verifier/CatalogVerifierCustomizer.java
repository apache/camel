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
package org.apache.camel.component.extension.verifier;

public class CatalogVerifierCustomizer {
    private boolean includeUnknown = true;
    private boolean includeRequired = true;
    private boolean includeInvalidBoolean = true;
    private boolean includeInvalidInteger = true;
    private boolean includeInvalidNumber = true;
    private boolean includeInvalidEnum = true;

    public boolean isIncludeUnknown() {
        return includeUnknown;
    }

    public void setIncludeUnknown(boolean includeUnknown) {
        this.includeUnknown = includeUnknown;
    }

    public boolean isIncludeRequired() {
        return includeRequired;
    }

    public void setIncludeRequired(boolean includeRequired) {
        this.includeRequired = includeRequired;
    }

    public boolean isIncludeInvalidBoolean() {
        return includeInvalidBoolean;
    }

    public void setIncludeInvalidBoolean(boolean includeInvalidBoolean) {
        this.includeInvalidBoolean = includeInvalidBoolean;
    }

    public boolean isIncludeInvalidInteger() {
        return includeInvalidInteger;
    }

    public void setIncludeInvalidInteger(boolean includeInvalidInteger) {
        this.includeInvalidInteger = includeInvalidInteger;
    }

    public boolean isIncludeInvalidNumber() {
        return includeInvalidNumber;
    }

    public void setIncludeInvalidNumber(boolean includeInvalidNumber) {
        this.includeInvalidNumber = includeInvalidNumber;
    }

    public boolean isIncludeInvalidEnum() {
        return includeInvalidEnum;
    }

    public void setIncludeInvalidEnum(boolean includeInvalidEnum) {
        this.includeInvalidEnum = includeInvalidEnum;
    }

    public CatalogVerifierCustomizer excludeUnknown() {
        this.includeUnknown = false;
        return this;
    }

    public CatalogVerifierCustomizer excludeRequired() {
        this.includeRequired = false;
        return this;
    }

    public CatalogVerifierCustomizer excludeInvalidBoolean() {
        this.includeInvalidBoolean = false;
        return this;
    }

    public CatalogVerifierCustomizer excludeInvalidInteger() {
        this.includeInvalidInteger = false;
        return this;
    }

    public CatalogVerifierCustomizer excludeInvalidNumber() {
        this.includeInvalidNumber = false;
        return this;
    }

    public CatalogVerifierCustomizer excludeInvalidEnum() {
        this.includeInvalidEnum = false;
        return this;
    }
}
