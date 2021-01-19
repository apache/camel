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
package org.apache.camel.component.salesforce.api.dto.bulkv2;

import java.time.Instant;

public abstract class JobBase extends AbstractBulkDTO {

    protected String id;
    protected JobTypeEnum jobType;
    protected ColumnDelimiterEnum columnDelimiter;
    protected ContentTypeEnum contentType;
    protected LineEndingEnum lineEnding;
    protected String object;
    protected String apiVersion;
    protected String createdById;
    protected Instant createdDate;
    protected JobStateEnum state;
    protected ConcurrencyModeEnum concurrencyMode;
    protected Instant systemModstamp;
    protected Integer retries;
    protected Long totalProcessingTime;
    protected Long numberRecordsProcessed;

    public JobTypeEnum getJobType() {
        return jobType;
    }

    public void setJobType(JobTypeEnum jobType) {
        this.jobType = jobType;
    }

    public ColumnDelimiterEnum getColumnDelimiter() {
        return columnDelimiter;
    }

    public void setColumnDelimiter(ColumnDelimiterEnum columnDelimiter) {
        this.columnDelimiter = columnDelimiter;
    }

    public ContentTypeEnum getContentType() {
        return contentType;
    }

    public void setContentType(ContentTypeEnum contentType) {
        this.contentType = contentType;
    }

    public LineEndingEnum getLineEnding() {
        return lineEnding;
    }

    public void setLineEnding(LineEndingEnum lineEnding) {
        this.lineEnding = lineEnding;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getCreatedById() {
        return createdById;
    }

    public void setCreatedById(String createdById) {
        this.createdById = createdById;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public JobStateEnum getState() {
        return state;
    }

    public void setState(JobStateEnum state) {
        this.state = state;
    }

    public ConcurrencyModeEnum getConcurrencyMode() {
        return concurrencyMode;
    }

    public void setConcurrencyMode(ConcurrencyModeEnum concurrencyMode) {
        this.concurrencyMode = concurrencyMode;
    }

    public Instant getSystemModstamp() {
        return systemModstamp;
    }

    public void setSystemModstamp(Instant systemModstamp) {
        this.systemModstamp = systemModstamp;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public Long getTotalProcessingTime() {
        return totalProcessingTime;
    }

    public void setTotalProcessingTime(Long totalProcessingTime) {
        this.totalProcessingTime = totalProcessingTime;
    }

    public Long getNumberRecordsProcessed() {
        return numberRecordsProcessed;
    }

    public void setNumberRecordsProcessed(Long numberRecordsProcessed) {
        this.numberRecordsProcessed = numberRecordsProcessed;
    }
}
