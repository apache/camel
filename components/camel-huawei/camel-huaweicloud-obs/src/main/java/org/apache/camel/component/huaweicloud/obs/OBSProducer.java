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
package org.apache.camel.component.huaweicloud.obs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.CreateBucketRequest;
import com.obs.services.model.HeaderResponse;
import com.obs.services.model.ListBucketsRequest;
import com.obs.services.model.ListBucketsResult;
import com.obs.services.model.ObsBucket;
import org.apache.camel.Exchange;
import org.apache.camel.component.huaweicloud.obs.constants.OBSConstants;
import org.apache.camel.component.huaweicloud.obs.constants.OBSOperations;
import org.apache.camel.component.huaweicloud.obs.constants.OBSProperties;
import org.apache.camel.component.huaweicloud.obs.models.ClientConfigurations;
import org.apache.camel.component.huaweicloud.obs.models.OBSRegion;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OBSProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(OBSProducer.class);
    private OBSEndpoint endpoint;
    private ClientConfigurations clientConfigurations;
    private ObsClient obsClient;
    private Gson gson;

    public OBSProducer(OBSEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        this.clientConfigurations = new ClientConfigurations();
        this.obsClient = this.endpoint.initClient();
        this.gson = new Gson();
    }

    public void process(Exchange exchange) throws Exception {
        updateClientConfigs(exchange);

        switch (clientConfigurations.getOperation()) {
            case OBSOperations.LIST_BUCKETS:
                listBuckets(exchange);
                break;
            case OBSOperations.CREATE_BUCKET:
                createBucket(exchange);
                break;
            case OBSOperations.DELETE_BUCKET:
                deleteBucket(exchange);
                break;
            case OBSOperations.CHECK_BUCKET_EXISTS:
                checkBucketExists(exchange);
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("%s is not a supported operation", clientConfigurations.getOperation()));
        }
    }

    /**
     * Perform list buckets operation
     *
     * @param exchange
     */
    private void listBuckets(Exchange exchange) throws ObsException {
        // invoke list buckets method and map response object to exchange body
        ListBucketsRequest request = new ListBucketsRequest();
        ListBucketsResult response = obsClient.listBucketsV2(request);
        exchange.getMessage().setBody(gson.toJson(response.getBuckets()));
    }

    /**
     * Perform create bucket operation
     *
     * @param exchange
     */
    private void createBucket(Exchange exchange) throws ObsException {
        CreateBucketRequest request = null;

        // checking if user inputted exchange body containing bucket information. Body must be a CreateBucketRequest or a valid JSON string (Advanced users)
        Object exchangeBody = exchange.getMessage().getBody();
        if (exchangeBody instanceof CreateBucketRequest) {
            request = (CreateBucketRequest) exchangeBody;
        } else if (exchangeBody instanceof String) {
            String strBody = (String) exchangeBody;
            try {
                request = new ObjectMapper().readValue(strBody, CreateBucketRequest.class);
            } catch (JsonProcessingException e) {
                LOG.warn(
                        "String request body must be a valid JSON representation of a CreateBucketRequest. Attempting to create a bucket from endpoint parameters");
            }
        }

        // if no CreateBucketRequest was found in the exchange body, then create one from endpoint parameters (Basic users)
        if (request == null) {
            // check for bucket name, which is mandatory to create a new bucket
            if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())) {
                LOG.error("No bucket name given");
                throw new IllegalArgumentException("Bucket name is mandatory to create bucket");
            }

            // check for bucket location, which is optional to create a new bucket
            if (ObjectHelper.isEmpty(clientConfigurations.getBucketLocation())) {
                LOG.warn("No bucket location given, defaulting to '" + OBSConstants.DEFAULT_LOCATION + "'");
                clientConfigurations.setBucketLocation(OBSConstants.DEFAULT_LOCATION);
            }
            // verify valid bucket location
            OBSRegion.checkValidRegion(clientConfigurations.getBucketLocation());

            request = new CreateBucketRequest(clientConfigurations.getBucketName(), clientConfigurations.getBucketLocation());
        }

        // invoke create bucket method and map response object to exchange body
        ObsBucket response = obsClient.createBucket(request);
        exchange.getMessage().setBody(gson.toJson(response));
    }

    /**
     * Perform delete bucket operation
     *
     * @param exchange
     */
    private void deleteBucket(Exchange exchange) throws ObsException {
        // check for bucket name, which is mandatory to delete a bucket
        if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())) {
            LOG.error("No bucket name given");
            throw new IllegalArgumentException("Bucket name is mandatory to delete bucket");
        }

        // invoke delete bucket method and map response object to exchange body
        HeaderResponse response = obsClient.deleteBucket(clientConfigurations.getBucketName());
        exchange.getMessage().setBody(gson.toJson(response.getResponseHeaders()));
    }

    /**
     * Perform check bucket exists operation
     *
     * @param exchange
     */
    private void checkBucketExists(Exchange exchange) throws ObsException {
        // check for bucket name, which is mandatory to check if a bucket exists
        if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())) {
            LOG.error("No bucket name given");
            throw new IllegalArgumentException("Bucket name is mandatory to check if bucket exists");
        }

        // invoke check bucket exists method and map response to exchange property
        boolean bucketExists = obsClient.headBucket(clientConfigurations.getBucketName());
        exchange.setProperty(OBSProperties.BUCKET_EXISTS, bucketExists);
    }

    /**
     * Update dynamic client configurations. Some endpoint parameters (operation, and bucket name and location) can also
     * be passed via exchange properties, so they can be updated between each transaction. Since they can change, we
     * must clear the previous transaction and update these parameters with their new values
     *
     * @param exchange
     */
    private void updateClientConfigs(Exchange exchange) {
        resetDynamicConfigs();

        // checking for required operation (exchange overrides endpoint operation if both are provided)
        if (ObjectHelper.isEmpty(exchange.getProperty(OBSProperties.OPERATION))
                && ObjectHelper.isEmpty(endpoint.getOperation())) {
            LOG.error("No operation name given. Cannot proceed with OBS operations.");
            throw new IllegalArgumentException("Operation name not found");
        } else {
            clientConfigurations.setOperation(
                    ObjectHelper.isNotEmpty(exchange.getProperty(OBSProperties.OPERATION))
                            ? (String) exchange.getProperty(OBSProperties.OPERATION)
                            : endpoint.getOperation());
        }

        // checking for optional bucketName (exchange overrides endpoint bucketName if both are provided)
        if (ObjectHelper.isNotEmpty(exchange.getProperty(OBSProperties.BUCKET_NAME))
                || ObjectHelper.isNotEmpty(endpoint.getBucketName())) {
            clientConfigurations.setBucketName(
                    ObjectHelper.isNotEmpty(exchange.getProperty(OBSProperties.BUCKET_NAME))
                            ? (String) exchange.getProperty(OBSProperties.BUCKET_NAME)
                            : endpoint.getBucketName());
        }

        // checking for optional bucketLocation (exchange overrides endpoint bucketLocation if both are provided)
        if (ObjectHelper.isNotEmpty(exchange.getProperty(OBSProperties.BUCKET_LOCATION))
                || ObjectHelper.isNotEmpty(endpoint.getBucketLocation())) {
            clientConfigurations.setBucketLocation(
                    ObjectHelper.isNotEmpty(exchange.getProperty(OBSProperties.BUCKET_LOCATION))
                            ? (String) exchange.getProperty(OBSProperties.BUCKET_LOCATION)
                            : endpoint.getBucketLocation());
        }
    }

    /**
     * Set all dynamic configurations to null
     */
    private void resetDynamicConfigs() {
        clientConfigurations.setOperation(null);
        clientConfigurations.setBucketName(null);
        clientConfigurations.setBucketLocation(null);
    }
}
