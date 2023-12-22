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
package org.apache.camel.component.salesforce.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.commons.lang3.reflect.FieldUtils;

public class FieldsToNullPropertyFilter extends SimpleBeanPropertyFilter {

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer)
            throws Exception {

        AbstractSObjectBase sob = (AbstractSObjectBase) pojo;
        String fieldName = writer.getName();
        Object fieldValue = null;
        boolean failedToReadFieldValue = false;
        try {
            fieldValue = FieldUtils.readField(pojo, fieldName, true);
        } catch (IllegalArgumentException e) {
            // This happens if the backing field for the getter doesn't match the name provided to @JsonProperty
            // This is expected to happen in the case of blob fields, e.g., ContentVersion.getVersionDataUrl(),
            // whose backing property is specified as @JsonData("VersionData")
            failedToReadFieldValue = true;
        }
        if (sob.getFieldsToNull().contains(writer.getName()) || fieldValue != null || failedToReadFieldValue) {
            writer.serializeAsField(pojo, jgen, provider);
        } else {
            writer.serializeAsOmittedField(pojo, jgen, provider);
        }
    }
}
