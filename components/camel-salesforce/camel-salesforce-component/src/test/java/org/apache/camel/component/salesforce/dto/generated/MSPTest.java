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
package org.apache.camel.component.salesforce.dto.generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import org.apache.camel.component.salesforce.api.MultiSelectPicklistConverter;
import org.apache.camel.component.salesforce.api.MultiSelectPicklistDeserializer;
import org.apache.camel.component.salesforce.api.MultiSelectPicklistSerializer;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;

/**
 * Sample POJO for MSP tests.
 */
//CHECKSTYLE:OFF
@XStreamAlias("MSPTest")
public class MSPTest extends AbstractSObjectBase {

    public MSPTest() {
        getAttributes().setType("MSPTest");
    }

    @XStreamConverter(MultiSelectPicklistConverter.class)
    private MSPEnum[] MspField;

    @JsonProperty("MspField")
    @JsonSerialize(using = MultiSelectPicklistSerializer.class)
    @JsonInclude(value = Include.ALWAYS)
    public MSPEnum[] getMspField() {
        return MspField;
    }

    @JsonProperty("MspField")
    @JsonDeserialize(using = MultiSelectPicklistDeserializer.class)
    public void setMspField(MSPEnum[] mspField) {
        this.MspField = mspField;
    }

    @JsonDeserialize
    public enum MSPEnum {

        // Value1
        VALUE1("Value1"),
        // Value1
        VALUE2("Value2"),
        // Value1
        VALUE3("Value3");

        final String value;

        private MSPEnum(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static MSPEnum fromValue(String value) {
            for (MSPEnum e : MSPEnum.values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException(value);
        }
    }
}
//CHECKSTYLE:ON
