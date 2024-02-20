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
package org.apache.camel.maven.dsl.yaml.support;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.util.StringHelper;

public class Schema {
    @JsonProperty
    @JsonAlias({ "dataformat", "language", "other", "model" })
    public ObjectNode meta;
    @JsonProperty
    public ObjectNode properties;
    @JsonIgnore
    public ObjectNode exchangeProperties;

    public Schema() {
    }

    public Schema(ObjectNode meta, ObjectNode properties) {
        this.meta = meta;
        this.properties = properties;
    }

    public JsonNode property(String name) {
        return properties.at("/" + StringHelper.dashToCamelCase(name));
    }

    public JsonNode description(String name) {
        return properties.at("/" + StringHelper.dashToCamelCase(name) + "/description");
    }

    public JsonNode displayName(String name) {
        return properties.at("/" + StringHelper.dashToCamelCase(name) + "/displayName");
    }

    public JsonNode defaultValue(String name) {
        return properties.at("/" + StringHelper.dashToCamelCase(name) + "/defaultValue");
    }

    public JsonNode isSecret(String name) {
        return properties.at("/" + StringHelper.dashToCamelCase(name) + "/secret");
    }
}
