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
package org.apache.camel.maven.htmlxlsx.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class Components {

    private Map<String, List<EipAttribute>> attributeMap = new HashMap<>();

    @JsonIgnore
    private final ObjectMapper objectMapper
            = JsonMapper.builder().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY).build();

    @JsonAnySetter
    public void setAttribute(String key, Object value) throws JacksonException {

        List<EipAttribute> listValue;

        if (value instanceof String) {
            EipAttribute eipAttribute
                    = objectMapper.readValue(String.format("{\"%s\":\"%s\"}", key, value), EipAttribute.class);
            listValue = Collections.singletonList(eipAttribute);
        } else if (!(value instanceof List)) {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            EipAttribute eipAttribute = objectMapper.readValue(json, EipAttribute.class);
            listValue = Collections.singletonList(eipAttribute);
        } else {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            listValue = objectMapper.readValue(json, new TypeReference<List<EipAttribute>>() {

            });
        }

        attributeMap.put(key, listValue);
    }

    public Map<String, List<EipAttribute>> getAttributeMap() {

        return attributeMap;
    }

    public void setAttributeMap(Map<String, List<EipAttribute>> attributeMap) {

        this.attributeMap = attributeMap;
    }

    @Override
    public String toString() {

        return "Components{" +
               "attributeMap=" + attributeMap +
               '}';
    }
}
