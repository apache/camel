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
package org.apache.camel.component.salesforce.dto.generated;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;

/**
 * Salesforce DTO for SObject Task
 */
//CHECKSTYLE:OFF
@XStreamAlias("Task")
public class Task extends AbstractSObjectBase {

    public Task() {
        getAttributes().setType("Task");
    }

    private ZonedDateTime ActivityDate;

    private String Description;

    @JsonProperty("ActivityDate")
    public ZonedDateTime getActivityDate() {
        return ActivityDate;
    }

    @JsonProperty("Description")
    public String getDescription() {
        return Description;
    }

    @JsonProperty("ActivityDate")
    public void setActivityDate(final ZonedDateTime given) {
        ActivityDate = given;
    }

    @JsonProperty("Description")
    public void setDescription(final String description) {
        Description = description;
    }

    @XStreamAlias("What")
    private AbstractSObjectBase What;

    @JsonProperty("What")
    public AbstractSObjectBase getWhat() {
        return this.What;
    }

    @JsonProperty("What")
    public void setWhat(AbstractSObjectBase What) {
        this.What = What;
    }
}
//CHECKSTYLE:ON
