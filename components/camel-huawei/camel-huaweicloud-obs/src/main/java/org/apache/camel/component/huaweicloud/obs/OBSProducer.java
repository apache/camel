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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.BucketMetadataInfoRequest;
import com.obs.services.model.BucketMetadataInfoResult;
import com.obs.services.model.CreateBucketRequest;
import com.obs.services.model.HeaderResponse;
import com.obs.services.model.ListBucketsRequest;
import com.obs.services.model.ListBucketsResult;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObsBucket;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PutObjectResult;
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
    private ObsClient obsClient;
    private Gson gson;

    public OBSProducer(OBSEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        this.gson = new Gson();
    }

    public void process(Exchange exchange) throws Exception {

        ClientConfigurations clientConfigurations = new ClientConfigurations();

        if (obsClient == null) {
            this.obsClient = endpoint.initClient();
        }

        updateClientConfigs(exchange, clientConfigurations);

        switch (clientConfigurations.getOperation()) {
            case OBSOperations.LIST_BUCKETS:
                listBuckets(exchange);
                break;
            case OBSOperations.CREATE_BUCKET:
                createBucket(exchange, clientConfigurations);
                break;
            case OBSOperations.DELETE_BUCKET:
                deleteBucket(exchange, clientConfigurations);
                break;
            case OBSOperations.CHECK_BUCKET_EXISTS:
                checkBucketExists(exchange, clientConfigurations);
                break;
            case OBSOperations.GET_BUCKET_METADATA:
                getBucketMetadata(exchange, clientConfigurations);
                break;
            case OBSOperations.LIST_OBJECTS:
                listObjects(exchange, clientConfigurations);
                break;
            case OBSOperations.GET_OBJECT:
                getObject(exchange, clientConfigurations);
                break;
            case OBSOperations.PUT_OBJECT:
                putObject(exchange, clientConfigurations);
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("%s is not a supported operation", clientConfigurations.getOperation()));
        }
    }

    private void putObject(Exchange exchange, ClientConfigurations clientConfigurations) throws IOException {

        Object body = exchange.getMessage().getBody();

        // if body doesn't contain File, then user must pass object name. Bucket name is mandatory in all case
        if ((ObjectHelper.isEmpty(clientConfigurations.getBucketName()) ||
                ObjectHelper.isEmpty(clientConfigurations.getObjectName())) && !(body instanceof File)) {
            throw new IllegalArgumentException("Bucket and object names are mandatory to put objects into bucket");
        }

        // check if bucket exists. if not, create one
        LOG.trace("Checking if bucket {} exists", clientConfigurations.getBucketName());
        if (!obsClient.headBucket(clientConfigurations.getBucketName())) {
            LOG.warn("No bucket found with name {}. Attempting to create", clientConfigurations.getBucketName());
            OBSRegion.checkValidRegion(clientConfigurations.getBucketLocation());
            CreateBucketRequest request = new CreateBucketRequest(
                    clientConfigurations.getBucketName(),
                    clientConfigurations.getBucketLocation());

            obsClient.createBucket(request);
            LOG.warn("Bucket with name {} created. Continuing to upload object into it", request.getBucketName());
        }

        PutObjectResult putObjectResult = null;

        if (body instanceof File) {

            LOG.trace("Exchange payload is of type File");

            // user file name by default if user has not over-riden it
            String objectName = ObjectHelper.isEmpty(clientConfigurations.getObjectName())
                    ? ((File) body).getName()
                    : clientConfigurations.getObjectName();

            putObjectResult = obsClient.putObject(clientConfigurations.getBucketName(),
                    objectName, (File) body);

        } else if (body instanceof String) {
            // the string content will be stored in the remote object
            LOG.trace("Writing text body into an object");
            InputStream stream = new ByteArrayInputStream(((String) body).getBytes());
            putObjectResult = obsClient.putObject(clientConfigurations.getBucketName(),
                    clientConfigurations.getObjectName(), stream);
            stream.close();

        } else if (body instanceof InputStream) {
            // this covers miscellaneous file types
            LOG.trace("Exchange payload is of type InputStream");
            putObjectResult = obsClient.putObject(clientConfigurations.getBucketName(),
                    clientConfigurations.getObjectName(), (InputStream) body);

        } else {
            throw new IllegalArgumentException("Body should be of type file, string or an input stream");
        }
        exchange.getMessage().setBody(gson.toJson(putObjectResult));
    }

    /**
     * downloads an object from remote OBS bucket
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void getObject(Exchange exchange, ClientConfigurations clientConfigurations) {
        if (ObjectHelper.isEmpty(clientConfigurations.getBucketName()) ||
                ObjectHelper.isEmpty(clientConfigurations.getObjectName())) {
            throw new IllegalArgumentException("Bucket and object names are mandatory to get objects");
        }

        LOG.debug("Downloading remote obs object {} from bucket {}", clientConfigurations.getObjectName(),
                clientConfigurations.getBucketLocation());

        ObsObject obsObject = obsClient
                .getObject(clientConfigurations.getBucketName(), clientConfigurations.getObjectName());

        LOG.debug("Successfully downloaded obs object {}", clientConfigurations.getObjectName());

        OBSUtils.mapObsObject(exchange, obsObject);
    }

    /**
     * Perform list buckets operation
     *
     * @param  exchange
     * @throws ObsException
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
     * @param  exchange
     * @param  clientConfigurations
     * @throws ObsException
     */
    private void createBucket(Exchange exchange, ClientConfigurations clientConfigurations) throws ObsException {
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
                LOG.warn("No bucket location given, defaulting to '{}'", OBSConstants.DEFAULT_LOCATION);
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
     * @param  exchange
     * @param  clientConfigurations
     * @throws ObsException
     */
    private void deleteBucket(Exchange exchange, ClientConfigurations clientConfigurations) throws ObsException {
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
     * @param  exchange
     * @param  clientConfigurations
     * @throws ObsException
     */
    private void checkBucketExists(Exchange exchange, ClientConfigurations clientConfigurations) throws ObsException {
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
     * Perform get bucket metadata operation
     *
     * @param  exchange
     * @param  clientConfigurations
     * @throws ObsException
     */
    private void getBucketMetadata(Exchange exchange, ClientConfigurations clientConfigurations) throws ObsException {
        // check for bucket name, which is mandatory to get bucket metadata
        if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())) {
            LOG.error("No bucket name given");
            throw new IllegalArgumentException("Bucket name is mandatory to get bucket metadata");
        }

        // invoke get bucket metadata method and map response object to exchange body
        BucketMetadataInfoRequest request = new BucketMetadataInfoRequest(clientConfigurations.getBucketName());
        BucketMetadataInfoResult response = obsClient.getBucketMetadata(request);
        exchange.getMessage().setBody(gson.toJson(response));
    }

    /**
     * Perform list objects operation
     *
     * @param  exchange
     * @param  clientConfigurations
     * @throws ObsException
     */
    private void listObjects(Exchange exchange, ClientConfigurations clientConfigurations) throws ObsException {
        ListObjectsRequest request = null;

        // checking if user inputted exchange body containing list objects information. Body must be a ListObjectsRequest or a valid JSON string (Advanced users)
        Object exchangeBody = exchange.getMessage().getBody();
        if (exchangeBody instanceof ListObjectsRequest) {
            request = (ListObjectsRequest) exchangeBody;
        } else if (exchangeBody instanceof String) {
            String strBody = (String) exchangeBody;
            try {
                request = new ObjectMapper().readValue(strBody, ListObjectsRequest.class);
            } catch (JsonProcessingException e) {
                LOG.warn(
                        "String request body must be a valid JSON representation of a ListObjectsRequest. Attempting to list objects from endpoint parameters");
            }
        }

        // if no ListObjectsRequest was found in the exchange body, then list from endpoint parameters (Basic users)
        if (request == null) {
            // check for bucket name, which is mandatory to list objects
            if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())) {
                LOG.error("No bucket name given");
                throw new IllegalArgumentException("Bucket name is mandatory to list objects");
            }
            request = new ListObjectsRequest(clientConfigurations.getBucketName());
        }

        // invoke list objects method. Each result only holds a maximum of 1000 objects, so keep listing each object until all objects have been listed
        ObjectListing result;
        List<ObsObject> objects = new ArrayList<>();
        do {
            result = obsClient.listObjects(request);
            objects.addAll(result.getObjects());
            request.setMarker(result.getNextMarker());
        } while (result.isTruncated());

        exchange.getMessage().setBody(gson.toJson(objects));
    }

    /**
     * Update dynamic client configurations. Some endpoint parameters (operation, and bucket name and location) can also
     * be passed via exchange properties, so they can be updated between each transaction. Since they can change, we
     * must clear the previous transaction and update these parameters with their new values
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void updateClientConfigs(Exchange exchange, ClientConfigurations clientConfigurations) {

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

        // checking for optional object name (exchange overrides endpoint bucketLocation if both are provided)
        if (ObjectHelper.isNotEmpty(exchange.getProperty(OBSProperties.OBJECT_NAME))
                || ObjectHelper.isNotEmpty(endpoint.getObjectName())) {
            clientConfigurations.setObjectName(
                    ObjectHelper.isNotEmpty(exchange.getProperty(OBSProperties.OBJECT_NAME))
                            ? (String) exchange.getProperty(OBSProperties.OBJECT_NAME)
                            : endpoint.getObjectName());
        }
    }
}
