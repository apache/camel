/**
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
package org.apache.camel.component.salesforce.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

//CHECKSTYLE:OFF
public class RecentItem extends AbstractDTOBase {

    // WARNING: these fields have case sensitive names,
    // the field name MUST match the field name used by Salesforce
    // DO NOT change these field names to camel case!!!
    private Attributes attributes;
    private String Id;
    private String Name;

    public Attributes getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    @JsonProperty("Id")
    public String getId() {
        return Id;
    }

    @JsonProperty("Id")
    public void setId(String id) {
        this.Id = id;
    }

    @JsonProperty("Name")
    public String getName() {
        return Name;
    }

    @JsonProperty("Name")
    public void setName(String name) {
        this.Name = name;
    }
}
//CHECKSTYLE:ON
