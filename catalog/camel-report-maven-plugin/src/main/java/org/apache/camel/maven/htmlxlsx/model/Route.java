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

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class Route {

    private String customId;

    private int exchangesTotal;

    private String id;

    private int totalProcessingTime;

    private Components components;

    private Map<String, Object> componentsMap = new HashMap<>();

    @JsonAnySetter
    public void setComponent(String name, Object value) {

        componentsMap.put(name, value);
    }

    public String getCustomId() {

        return customId;
    }

    public void setCustomId(String customId) {

        this.customId = customId;
    }

    public int getExchangesTotal() {

        return exchangesTotal;
    }

    public void setExchangesTotal(int exchangesTotal) {

        this.exchangesTotal = exchangesTotal;
    }

    public String getId() {

        return id;
    }

    public void setId(String id) {

        this.id = id;
    }

    public int getTotalProcessingTime() {

        return totalProcessingTime;
    }

    public void setTotalProcessingTime(int totalProcessingTime) {

        this.totalProcessingTime = totalProcessingTime;
    }

    public Components getComponents() {

        return components;
    }

    public void setComponents(Components components) {

        this.components = components;
    }

    @JsonAnyGetter
    public Map<String, Object> getComponentsMap() {

        return componentsMap;
    }

    public void setComponentsMap(Map<String, Object> componentsMap) {

        this.componentsMap = componentsMap;
    }

    @Override
    public String toString() {

        return "Route{" +
               "customId='" + customId + '\'' +
               ", exchangesTotal=" + exchangesTotal +
               ", id='" + id + '\'' +
               ", totalProcessingTime=" + totalProcessingTime +
               ", components=" + components +
               ", componentsMap=" + componentsMap +
               '}';
    }
}
