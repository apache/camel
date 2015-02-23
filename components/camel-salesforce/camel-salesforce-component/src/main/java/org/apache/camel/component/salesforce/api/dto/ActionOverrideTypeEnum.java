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
package org.apache.camel.component.salesforce.api.dto;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonValue;
import org.codehaus.jackson.map.annotate.JsonDeserialize;

@JsonDeserialize
public enum ActionOverrideTypeEnum {

    // The override uses a custom override provided by an installed package.
    // If there isn’t one available, the standard Salesforce behavior is used.
    DEFAULT("default"),
    // The override uses behavior from an s-control.
    SCONTROL("scontrol"),
    // The override uses regular Salesforce behavior.
    STANDARD("standard"),
    // The override uses behavior from a Visualforce page.
    VISUALFORCE("visualforce");

    final String value;

    private ActionOverrideTypeEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    @JsonCreator
    public static ActionOverrideTypeEnum fromValue(String value) {
        for (ActionOverrideTypeEnum e : ActionOverrideTypeEnum.values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException(value);
    }

}
