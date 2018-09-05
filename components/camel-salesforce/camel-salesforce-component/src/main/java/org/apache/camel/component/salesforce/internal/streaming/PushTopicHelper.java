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
package org.apache.camel.component.salesforce.internal.streaming;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.CamelException;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.SyncResponseCallback;
import org.apache.camel.component.salesforce.internal.dto.PushTopic;
import org.apache.camel.component.salesforce.internal.dto.QueryRecordsPushTopic;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushTopicHelper {
    private static final Logger LOG = LoggerFactory.getLogger(PushTopicHelper.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.createObjectMapper();
    private static final String PUSH_TOPIC_OBJECT_NAME = "PushTopic";
    private static final long API_TIMEOUT = 60; // Rest API call timeout
    private final SalesforceEndpointConfig config;
    private final String topicName;
    private final RestClient restClient;
    private final boolean preApi29;

    public PushTopicHelper(SalesforceEndpointConfig config, String topicName, RestClient restClient) {
        this.config = config;
        this.topicName = topicName;
        this.restClient = restClient;
        this.preApi29 = Double.valueOf(config.getApiVersion()) < 29.0;

        // validate notify fields right away
        if (preApi29 && (config.getNotifyForOperationCreate() != null
                || config.getNotifyForOperationDelete() != null
                || config.getNotifyForOperationUndelete() != null
                || config.getNotifyForOperationUpdate() != null)) {
            throw new IllegalArgumentException("NotifyForOperationCreate, NotifyForOperationDelete"
                + ", NotifyForOperationUndelete, and NotifyForOperationUpdate"
                + " are only supported since API version 29.0"
                + ", instead use NotifyForOperations");
        } else if (!preApi29 && config.getNotifyForOperations() != null) {
            throw new IllegalArgumentException("NotifyForOperations is readonly since API version 29.0"
                + ", instead use NotifyForOperationCreate, NotifyForOperationDelete"
                + ", NotifyForOperationUndelete, and NotifyForOperationUpdate");
        }
    }

    public void createOrUpdateTopic() throws CamelException {
        final String query = config.getSObjectQuery();

        final SyncResponseCallback callback = new SyncResponseCallback();
        // lookup Topic first
        try {
            // use SOQL to lookup Topic, since Name is not an external ID!!!
            restClient.query("SELECT Id, Name, Query, ApiVersion, IsActive, "
                    + "NotifyForFields, NotifyForOperations, NotifyForOperationCreate, "
                    + "NotifyForOperationDelete, NotifyForOperationUndelete, "
                    + "NotifyForOperationUpdate, Description "
                    + "FROM PushTopic WHERE Name = '" + topicName + "'",
                    Collections.emptyMap(),
                    callback);

            if (!callback.await(API_TIMEOUT, TimeUnit.SECONDS)) {
                throw new SalesforceException("API call timeout!", null);
            }
            final SalesforceException callbackException = callback.getException();
            if (callbackException != null) {
                throw callbackException;
            }
            QueryRecordsPushTopic records = OBJECT_MAPPER.readValue(callback.getResponse(),
                    QueryRecordsPushTopic.class);
            if (records.getTotalSize() == 1) {

                PushTopic topic = records.getRecords().get(0);
                LOG.info("Found existing topic {}: {}", topicName, topic);

                // check if we need to update topic
                final boolean notifyOperationsChanged;
                if (preApi29) {
                    notifyOperationsChanged =
                        notEquals(config.getNotifyForOperations(), topic.getNotifyForOperations());
                } else {
                    notifyOperationsChanged =
                        notEquals(config.getNotifyForOperationCreate(), topic.getNotifyForOperationCreate())
                        || notEquals(config.getNotifyForOperationDelete(), topic.getNotifyForOperationDelete())
                        || notEquals(config.getNotifyForOperationUndelete(), topic.getNotifyForOperationUndelete())
                        || notEquals(config.getNotifyForOperationUpdate(), topic.getNotifyForOperationUpdate());
                }
                if (!query.equals(topic.getQuery())
                    || notEquals(config.getNotifyForFields(), topic.getNotifyForFields())
                    || notifyOperationsChanged) {

                    if (!config.isUpdateTopic()) {
                        String msg = "Query doesn't match existing Topic and updateTopic is set to false";
                        throw new CamelException(msg);
                    }

                    // otherwise update the topic
                    updateTopic(topic.getId());
                }

            } else {
                createTopic();
            }

        } catch (SalesforceException e) {
            throw new CamelException(
                    String.format("Error retrieving Topic %s: %s", topicName, e.getMessage()),
                    e);
        } catch (IOException e) {
            throw new CamelException(
                    String.format("Un-marshaling error retrieving Topic %s: %s", topicName, e.getMessage()),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CamelException(
                    String.format("Un-marshaling error retrieving Topic %s: %s", topicName, e.getMessage()),
                    e);
        } finally {
            // close stream to close HttpConnection
            if (callback.getResponse() != null) {
                try {
                    callback.getResponse().close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void createTopic() throws CamelException {
        final PushTopic topic = new PushTopic();
        topic.setName(topicName);
        topic.setApiVersion(Double.valueOf(config.getApiVersion()));
        topic.setQuery(config.getSObjectQuery());
        topic.setDescription("Topic created by Camel Salesforce component");
        topic.setNotifyForFields(config.getNotifyForFields());
        if (preApi29) {
            topic.setNotifyForOperations(config.getNotifyForOperations());
        } else {
            topic.setNotifyForOperationCreate(config.getNotifyForOperationCreate());
            topic.setNotifyForOperationDelete(config.getNotifyForOperationDelete());
            topic.setNotifyForOperationUndelete(config.getNotifyForOperationUndelete());
            topic.setNotifyForOperationUpdate(config.getNotifyForOperationUpdate());
        }

        LOG.info("Creating Topic {}: {}", topicName, topic);
        final SyncResponseCallback callback = new SyncResponseCallback();
        try {
            restClient.createSObject(PUSH_TOPIC_OBJECT_NAME,
                    new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(topic)), Collections.emptyMap(), callback);

            if (!callback.await(API_TIMEOUT, TimeUnit.SECONDS)) {
                throw new SalesforceException("API call timeout!", null);
            }
            final SalesforceException callbackException = callback.getException();
            if (callbackException != null) {
                throw callbackException;
            }

            CreateSObjectResult result = OBJECT_MAPPER.readValue(callback.getResponse(), CreateSObjectResult.class);
            if (!result.getSuccess()) {
                final SalesforceException salesforceException = new SalesforceException(
                        result.getErrors(), HttpStatus.BAD_REQUEST_400);
                throw new CamelException(
                        String.format("Error creating Topic %s: %s", topicName, result.getErrors()),
                        salesforceException);
            }
        } catch (SalesforceException e) {
            throw new CamelException(
                    String.format("Error creating Topic %s: %s", topicName, e.getMessage()),
                    e);
        } catch (IOException e) {
            throw new CamelException(
                    String.format("Un-marshaling error creating Topic %s: %s", topicName, e.getMessage()),
                    e);
        } catch (InterruptedException e) {
            throw new CamelException(
                    String.format("Un-marshaling error creating Topic %s: %s", topicName, e.getMessage()),
                    e);
        } finally {
            if (callback.getResponse() != null) {
                try {
                    callback.getResponse().close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void updateTopic(String topicId) throws CamelException {
        final String query = config.getSObjectQuery();
        LOG.info("Updating Topic {} with Query [{}]", topicName, query);

        final SyncResponseCallback callback = new SyncResponseCallback();
        try {
            // update the query, notifyForFields and notifyForOperations fields
            final PushTopic topic = new PushTopic();
            topic.setQuery(query);
            topic.setNotifyForFields(config.getNotifyForFields());
            if (preApi29) {
                topic.setNotifyForOperations(config.getNotifyForOperations());
            } else {
                topic.setNotifyForOperationCreate(config.getNotifyForOperationCreate());
                topic.setNotifyForOperationDelete(config.getNotifyForOperationDelete());
                topic.setNotifyForOperationUndelete(config.getNotifyForOperationUndelete());
                topic.setNotifyForOperationUpdate(config.getNotifyForOperationUpdate());
            }

            restClient.updateSObject("PushTopic", topicId,
                    new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(topic)),
                    Collections.emptyMap(),
                    callback);

            if (!callback.await(API_TIMEOUT, TimeUnit.SECONDS)) {
                throw new SalesforceException("API call timeout!", null);
            }
            final SalesforceException callbackException = callback.getException();
            if (callbackException != null) {
                throw callbackException;
            }

        } catch (SalesforceException e) {
            throw new CamelException(
                    String.format("Error updating topic %s with query [%s] : %s", topicName, query, e.getMessage()),
                    e);
        } catch (InterruptedException e) {
            // reset interrupt status
            Thread.currentThread().interrupt();
            throw new CamelException(
                    String.format("Error updating topic %s with query [%s] : %s", topicName, query, e.getMessage()),
                    e);
        } catch (IOException e) {
            throw new CamelException(
                    String.format("Error updating topic %s with query [%s] : %s", topicName, query, e.getMessage()),
                    e);
        } finally {
            if (callback.getResponse() != null) {
                try {
                    callback.getResponse().close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private static <T> boolean notEquals(T o1, T o2) {
        return o1 != null && !o1.equals(o2);
    }

}