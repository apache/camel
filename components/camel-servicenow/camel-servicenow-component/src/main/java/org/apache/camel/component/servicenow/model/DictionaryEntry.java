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
package org.apache.camel.component.servicenow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DictionaryEntry {
    private final Reference internalType;
    private final Reference reference;
    private final Integer maxLength;
    private final boolean mandatory;

    @JsonCreator
    public DictionaryEntry(
        @JsonProperty(value = "internal_type") Reference internalType,
        @JsonProperty(value = "reference") Reference reference,
        @JsonProperty(value = "max_length") Integer maxLength,
        @JsonProperty(value = "mandatory", defaultValue = "false") boolean mandatory) {

        this.internalType = internalType;
        this.reference = reference;
        this.maxLength = maxLength;
        this.mandatory = mandatory;
    }

    public Reference getInternalType() {
        return internalType;
    }

    public Reference getReference() {
        return reference;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
