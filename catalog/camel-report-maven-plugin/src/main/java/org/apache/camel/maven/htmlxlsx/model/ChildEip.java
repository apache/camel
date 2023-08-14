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

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChildEip {

    private String id;

    private Map<String, Object> eipAttributeMap = new LinkedHashMap<>();

    @JsonIgnore
    private static ObjectMapper OBJECT_MAPPER;

    protected ObjectMapper objectMapper() {

        if (OBJECT_MAPPER == null) {
            OBJECT_MAPPER = new ObjectMapper();
            OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            OBJECT_MAPPER.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        }

        return OBJECT_MAPPER;
    }

    public String getId() {

        return id;
    }

    public void setId(String id) {

        this.id = id;
    }

    public Map<String, Object> getEipAttributeMap() {

        return eipAttributeMap;
    }

    public void setEipAttributeMap(Map<String, Object> eipAttributeMap) {

        this.eipAttributeMap = eipAttributeMap;
    }

    @JsonAnySetter
    public void setEipAttribute(String key, Object value) {

        if (value instanceof Map) {
            try {
                String childJson = objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(value);
                EipAttribute eipAttribute = objectMapper().readValue(childJson, EipAttribute.class);
                eipAttributeMap.put(key, eipAttribute);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            eipAttributeMap.put(key, value);
        }
    }

    @Override
    public String toString() {

        return "ChildEip{" +
               "id='" + id + '\'' +
               ", eipAttributes=" + eipAttributeMap +
               '}';
    }
}
