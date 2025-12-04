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

package org.apache.camel.dsl.jbang.core.model;

import java.util.Map;

import org.apache.camel.util.json.JsonObject;

public class InfraBaseDTO {

    private String alias;
    private String aliasImplementation;
    private String description;
    private Object serviceData;

    public InfraBaseDTO() {}

    public InfraBaseDTO(String alias, String aliasImplementation, String description, Object serviceData) {
        this.alias = alias;
        this.aliasImplementation = aliasImplementation;
        this.description = description;
        this.serviceData = serviceData;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getAliasImplementation() {
        return aliasImplementation;
    }

    public void setAliasImplementation(String aliasImplementation) {
        this.aliasImplementation = aliasImplementation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getServiceData() {
        return serviceData;
    }

    public void setServiceData(Object serviceData) {
        this.serviceData = serviceData;
    }

    public Map<String, Object> toMap() {
        JsonObject jo = new JsonObject();
        jo.put("alias", alias);
        if (aliasImplementation != null) {
            jo.put("aliasImplementation", aliasImplementation);
        }
        if (description != null) {
            jo.put("description", description);
        }
        if (serviceData != null) {
            jo.put("serviceData", serviceData);
        }
        return jo;
    }
}
