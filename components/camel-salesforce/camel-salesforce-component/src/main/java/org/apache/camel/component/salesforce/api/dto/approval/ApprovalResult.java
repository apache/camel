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
package org.apache.camel.component.salesforce.api.dto.approval;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalResult.ApprovalResultDeserializer;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalResult.Result;

@XStreamAlias("ProcessApprovalResult")
@JsonDeserialize(using = ApprovalResultDeserializer.class)
public final class ApprovalResult implements Serializable, Iterable<Result> {

    public static final class ApprovalResultDeserializer extends JsonDeserializer {

        private static final TypeReference<List<Result>> RESULTS_TYPE = new TypeReference<List<Result>>() {
        };

        @Override
        public Object deserialize(final JsonParser parser, final DeserializationContext context) throws IOException, JsonProcessingException {
            final List<Result> results = parser.readValueAs(RESULTS_TYPE);

            return new ApprovalResult(results);
        }

    }

    @XStreamAlias("ProcessApprovalResult")
    public static final class Result implements Serializable {

        private static final long serialVersionUID = 1L;

        @XStreamImplicit(itemFieldName = "actorIds")
        private final List<String> actorIds;

        private final String entityId;

        @XStreamImplicit(itemFieldName = "errors")
        private final List<RestError> errors;

        private final String instanceId;

        private final String instanceStatus;

        @XStreamImplicit(itemFieldName = "newWorkitemIds")
        private final List<String> newWorkitemIds;

        private final boolean success;

        @JsonCreator
        Result(@JsonProperty("actorIds")
        final List<String> actorIds, @JsonProperty("entityId")
        final String entityId, @JsonProperty("errors")
        final List<RestError> errors, @JsonProperty("instanceId")
        final String instanceId, @JsonProperty("instanceStatus")
        final String instanceStatus, @JsonProperty("newWorkitemIds")
        final List<String> newWorkitemIds, @JsonProperty("success")
        final boolean success) {
            this.actorIds = actorIds;
            this.entityId = entityId;
            this.errors = errors;
            this.instanceId = instanceId;
            this.instanceStatus = instanceStatus;
            this.newWorkitemIds = newWorkitemIds;
            this.success = success;
        }

        public List<String> getActorIds() {
            return actorIds;
        }

        public String getEntityId() {
            return entityId;
        }

        public List<RestError> getErrors() {
            return errors;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public String getInstanceStatus() {
            return instanceStatus;
        }

        public List<String> getNewWorkitemIds() {
            return newWorkitemIds;
        }

        public boolean isSuccess() {
            return success;
        }

    }

    private static final long serialVersionUID = 1L;

    @XStreamImplicit(itemFieldName = "ProcessApprovalResult")
    private final List<Result> results;

    public ApprovalResult() {
        this(new ArrayList<>());
    }

    private ApprovalResult(final List<Result> results) {
        this.results = results;
    }

    @Override
    public Iterator<Result> iterator() {
        return results.listIterator();
    }

    public int size() {
        return results.size();
    }
}
