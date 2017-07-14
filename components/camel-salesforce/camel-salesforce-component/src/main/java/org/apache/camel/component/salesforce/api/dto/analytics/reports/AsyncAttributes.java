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
package org.apache.camel.component.salesforce.api.dto.analytics.reports;


import java.time.ZonedDateTime;

/**
 * Async report attributes DTO.
 */
public class AsyncAttributes extends AbstractAttributesBase {

    private String id;
    private ReportStatusEnum status;
    private String ownerId;
    private ZonedDateTime completionDate;
    private ZonedDateTime requestDate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ReportStatusEnum getStatus() {
        return status;
    }

    public void setStatus(ReportStatusEnum status) {
        this.status = status;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public ZonedDateTime getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(ZonedDateTime completionDate) {
        this.completionDate = completionDate;
    }

    public ZonedDateTime getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(ZonedDateTime requestDate) {
        this.requestDate = requestDate;
    }
}
