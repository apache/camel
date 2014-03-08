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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelException;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.SyncResponseCallback;
import org.apache.camel.component.salesforce.internal.dto.PushTopic;
import org.apache.camel.component.salesforce.internal.dto.QueryRecordsPushTopic;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushTopicHelper {
    private static final Logger LOG = LoggerFactory.getLogger(PushTopicHelper.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PUSH_TOPIC_OBJECT_NAME = "PushTopic";
    private static final long API_TIMEOUT = 60; // Rest API call timeout
    private final SalesforceEndpointConfig config;
    private final String topicName;
    private final RestClient restClient;

    public PushTopicHelper(SalesforceEndpointConfig config, String topicName, RestClient restClient) {
        this.config = config;
        this.topicName = topicName;
        this.restClient = restClient;
    }

    public void createOrUpdateTopic() throws CamelException {
        final String query = config.getSObjectQuery();

        final SyncResponseCallback callback = new SyncResponseCallback();
        // lookup Topic first
        try {
            // use SOQL to lookup Topic, since Name is not an external ID!!!
            restClient.query("SELECT Id, Name, Query, ApiVersion, IsActive, "
                    + "NotifyForFields, NotifyForOperations, Description "
                    + "FROM PushTopic WHERE Name = '" + topicName + "'",
                    callback);

            if (!callback.await(API_TIMEOUT, TimeUnit.SECONDS)) {
                throw new SalesforceException("API call timeout!", null);
            }
            if (callback.getException() != null) {
                throw callback.getException();
            }
            QueryRecordsPushTopic records = OBJECT_MAPPER.readValue(callback.getResponse(),
                    QueryRecordsPushTopic.class);
            if (records.getTotalSize() == 1) {

                PushTopic topic = records.getRecords().get(0);
                LOG.info("Found existing topic {}: {}", topicName, topic);

                // check if we need to update topic query, notifyForFields or notifyForOperations
                if (!query.equals(topic.getQuery())
                        || (config.getNotifyForFields() != null
                                && !config.getNotifyForFields().equals(topic.getNotifyForFields()))
                        || (config.getNotifyForOperations() != null
                                && !config.getNotifyForOperations().equals(topic.getNotifyForOperations()))
                ) {

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
        topic.setNotifyForOperations(config.getNotifyForOperations());

        LOG.info("Creating Topic {}: {}", topicName, topic);
        final SyncResponseCallback callback = new SyncResponseCallback();
        try {
            restClient.createSObject(PUSH_TOPIC_OBJECT_NAME,
                    new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(topic)), callback);

            if (!callback.await(API_TIMEOUT, TimeUnit.SECONDS)) {
                throw new SalesforceException("API call timeout!", null);
            }
            if (callback.getException() != null) {
                throw callback.getException();
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
            topic.setNotifyForOperations(config.getNotifyForOperations());

            restClient.updateSObject("PushTopic", topicId,
                    new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(topic)),
                    callback);

            if (!callback.await(API_TIMEOUT, TimeUnit.SECONDS)) {
                throw new SalesforceException("API call timeout!", null);
            }
            if (callback.getException() != null) {
                throw callback.getException();
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

}