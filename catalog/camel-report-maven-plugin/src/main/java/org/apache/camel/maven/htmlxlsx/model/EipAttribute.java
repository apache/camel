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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

public class EipAttribute implements Comparable<EipAttribute> {

    @JsonIgnore
    private static ObjectMapper OBJECT_MAPPER;

    private String id;

    private int exchangesTotal;

    private int index;

    private int totalProcessingTime;

    private Properties properties = new Properties();

    private Map<String, List<ChildEip>> childEipMap = new LinkedHashMap<>();

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

    public int getExchangesTotal() {

        return exchangesTotal;
    }

    public void setExchangesTotal(int exchangesTotal) {

        this.exchangesTotal = exchangesTotal;
    }

    public int getIndex() {

        return index;
    }

    public void setIndex(int index) {

        this.index = index;
    }

    public int getTotalProcessingTime() {

        return totalProcessingTime;
    }

    public void setTotalProcessingTime(int totalProcessingTime) {

        this.totalProcessingTime = totalProcessingTime;
    }

    @JsonAnySetter
    public void setProperty(String key, Object value) {

        properties.put(key, value);

        if (value instanceof Map) {
            try {
                String childJson = objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(value);
                ChildEip childEip = objectMapper().readValue(childJson, ChildEip.class);
                if (childEip.getId() == null) {
                    childEip.setId(id + "-" + childEipMap.size());
                }
                childEipMap.put(key, Collections.singletonList(childEip));
                properties.remove(key);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else if (value instanceof List) {
            try {
                String childJson = objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(value);
                CollectionType javaType = objectMapper().getTypeFactory().constructCollectionType(List.class, ChildEip.class);
                List<ChildEip> childEipList = objectMapper().readValue(childJson, javaType);
                childEipMap.put(key, childEipList);
                properties.remove(key);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @JsonAnyGetter
    public Properties getProperties() {

        return properties;
    }

    public void setProperties(Properties properties) {

        this.properties = properties;
    }

    public Map<String, List<ChildEip>> getChildEipMap() {

        return childEipMap;
    }

    public void setChildEipMap(Map<String, List<ChildEip>> childEipMap) {

        this.childEipMap = childEipMap;
    }

    @Override
    public int compareTo(EipAttribute o) {

        if (o == null) {
            return 1;
        }
        return index - o.index;
    }

    @Override
    public boolean equals(Object o) {

        if (o == this) {
            return true;
        }

        if (!(o instanceof EipAttribute)) {
            return false;
        }

        return id.equals(((EipAttribute) o).id);
    }

    @Override
    public String toString() {

        return "EipAttribute{" +
               "id='" + id + '\'' +
               ", exchangesTotal=" + exchangesTotal +
               ", index=" + index +
               ", totalProcessingTime=" + totalProcessingTime +
               ", properties=" + properties +
               ", childEipMap=" + childEipMap +
               '}';
    }
}
