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
package org.apache.camel.component.arangodb.integration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class BaseDocumentEntity {

    @JsonProperty("_key")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String key;

    @JsonProperty("_id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String id;

    @JsonProperty("_rev")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String rev;

    public BaseDocumentEntity(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getId() {
        return id;
    }

    public String getRev() {
        return rev;
    }

}
