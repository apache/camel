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
package org.apache.camel.component.servicenow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.servicenow.annotations.ServiceNowSysParm;

@ServiceNowSysParm(name = "sysparm_exclude_reference_link", value = "true")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImportSetResult {
    private final String transformMap;
    private final String table;
    private final String displayName;
    private final String displayValue;
    private final String recordLink;
    private final String status;
    private final String sysId;

    @JsonCreator
    public ImportSetResult(
        @JsonProperty(value = "transform_map") String transformMap,
        @JsonProperty(value = "table", required = true) String table,
        @JsonProperty(value = "display_name") String displayName,
        @JsonProperty(value = "display_value") String displayValue,
        @JsonProperty(value = "record_link") String recordLink,
        @JsonProperty(value = "status", required = true) String status,
        @JsonProperty(value = "sys_id", required = true) String sysId) {

        this.transformMap = transformMap;
        this.table = table;
        this.displayName = displayName;
        this.displayValue = displayValue;
        this.recordLink = recordLink;
        this.status = status;
        this.sysId = sysId;
    }

    public String getTransformMap() {
        return transformMap;
    }

    public String getTable() {
        return table;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    public String getRecordLink() {
        return recordLink;
    }

    public String getStatus() {
        return status;
    }

    public String getSysId() {
        return sysId;
    }
}
