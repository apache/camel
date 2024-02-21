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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.camel.component.salesforce.api.StringMultiSelectPicklistDeserializer;
import org.apache.camel.component.salesforce.api.StringMultiSelectPicklistSerializer;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;

/**
 * Sample POJO for MSP tests using Strings instead of Constants.
 */
public class StringMSPTest extends AbstractSObjectBase {

    public StringMSPTest() {
        getAttributes().setType("MSPTest");
    }

    private java.lang.String[] MspField;

    @JsonProperty("MspField")
    @JsonSerialize(using = StringMultiSelectPicklistSerializer.class)
    public java.lang.String[] getMspField() {
        return MspField;
    }

    @JsonProperty("MspField")
    @JsonDeserialize(using = StringMultiSelectPicklistDeserializer.class)
    public void setMspField(java.lang.String[] mspField) {
        this.MspField = mspField;
    }
}
