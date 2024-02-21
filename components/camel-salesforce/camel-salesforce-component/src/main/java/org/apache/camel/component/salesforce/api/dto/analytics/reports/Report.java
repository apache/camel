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
package org.apache.camel.component.salesforce.api.dto.analytics.reports;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;

/**
 * Salesforce DTO for SObject Report
 */
public class Report extends AbstractSObjectBase {

    // Description
    private String Description;

    // DeveloperName
    private String DeveloperName;

    // NamespacePrefix
    private String NamespacePrefix;

    // LastRunDate
    private ZonedDateTime LastRunDate;

    // Format
    private FormatEnum Format;

    @JsonProperty("Description")
    public String getDescription() {
        return this.Description;
    }

    @JsonProperty("Description")
    public void setDescription(String Description) {
        this.Description = Description;
    }

    @JsonProperty("DeveloperName")
    public String getDeveloperName() {
        return this.DeveloperName;
    }

    @JsonProperty("DeveloperName")
    public void setDeveloperName(String DeveloperName) {
        this.DeveloperName = DeveloperName;
    }

    @JsonProperty("NamespacePrefix")
    public String getNamespacePrefix() {
        return this.NamespacePrefix;
    }

    @JsonProperty("NamespacePrefix")
    public void setNamespacePrefix(String NamespacePrefix) {
        this.NamespacePrefix = NamespacePrefix;
    }

    @JsonProperty("LastRunDate")
    public ZonedDateTime getLastRunDate() {
        return this.LastRunDate;
    }

    @JsonProperty("LastRunDate")
    public void setLastRunDate(ZonedDateTime LastRunDate) {
        this.LastRunDate = LastRunDate;
    }

    @JsonProperty("Format")
    public FormatEnum getFormat() {
        return this.Format;
    }

    @JsonProperty("Format")
    public void setFormat(FormatEnum Format) {
        this.Format = Format;
    }

}
