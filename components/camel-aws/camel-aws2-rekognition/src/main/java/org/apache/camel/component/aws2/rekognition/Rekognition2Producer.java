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
package org.apache.camel.component.aws2.rekognition;

import java.util.Collection;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.AssociateFacesRequest;
import software.amazon.awssdk.services.rekognition.model.AssociateFacesResponse;
import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.CreateUserRequest;
import software.amazon.awssdk.services.rekognition.model.CreateUserResponse;
import software.amazon.awssdk.services.rekognition.model.DeleteCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.DeleteFacesRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteFacesResponse;
import software.amazon.awssdk.services.rekognition.model.DeleteUserRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteUserResponse;
import software.amazon.awssdk.services.rekognition.model.DescribeCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.DescribeCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.DetectFacesRequest;
import software.amazon.awssdk.services.rekognition.model.DetectFacesResponse;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsFeatureName;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsSettings;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectProtectiveEquipmentRequest;
import software.amazon.awssdk.services.rekognition.model.DetectProtectiveEquipmentResponse;
import software.amazon.awssdk.services.rekognition.model.DetectTextFilters;
import software.amazon.awssdk.services.rekognition.model.DetectTextRequest;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.DisassociateFacesRequest;
import software.amazon.awssdk.services.rekognition.model.DisassociateFacesResponse;
import software.amazon.awssdk.services.rekognition.model.GetCelebrityInfoRequest;
import software.amazon.awssdk.services.rekognition.model.GetCelebrityInfoResponse;
import software.amazon.awssdk.services.rekognition.model.GetMediaAnalysisJobRequest;
import software.amazon.awssdk.services.rekognition.model.GetMediaAnalysisJobResponse;
import software.amazon.awssdk.services.rekognition.model.HumanLoopConfig;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.IndexFacesRequest;
import software.amazon.awssdk.services.rekognition.model.IndexFacesResponse;
import software.amazon.awssdk.services.rekognition.model.ListCollectionsRequest;
import software.amazon.awssdk.services.rekognition.model.ListCollectionsResponse;
import software.amazon.awssdk.services.rekognition.model.ListFacesRequest;
import software.amazon.awssdk.services.rekognition.model.ListFacesResponse;
import software.amazon.awssdk.services.rekognition.model.ListMediaAnalysisJobsRequest;
import software.amazon.awssdk.services.rekognition.model.ListMediaAnalysisJobsResponse;
import software.amazon.awssdk.services.rekognition.model.ListUsersRequest;
import software.amazon.awssdk.services.rekognition.model.ListUsersResponse;
import software.amazon.awssdk.services.rekognition.model.MediaAnalysisInput;
import software.amazon.awssdk.services.rekognition.model.MediaAnalysisOperationsConfig;
import software.amazon.awssdk.services.rekognition.model.MediaAnalysisOutputConfig;
import software.amazon.awssdk.services.rekognition.model.ProtectiveEquipmentSummarizationAttributes;
import software.amazon.awssdk.services.rekognition.model.RecognizeCelebritiesRequest;
import software.amazon.awssdk.services.rekognition.model.RecognizeCelebritiesResponse;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageRequest;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageResponse;
import software.amazon.awssdk.services.rekognition.model.SearchFacesRequest;
import software.amazon.awssdk.services.rekognition.model.SearchFacesResponse;
import software.amazon.awssdk.services.rekognition.model.SearchUsersByImageRequest;
import software.amazon.awssdk.services.rekognition.model.SearchUsersByImageResponse;
import software.amazon.awssdk.services.rekognition.model.SearchUsersRequest;
import software.amazon.awssdk.services.rekognition.model.SearchUsersResponse;
import software.amazon.awssdk.services.rekognition.model.StartMediaAnalysisJobRequest;
import software.amazon.awssdk.services.rekognition.model.StartMediaAnalysisJobResponse;

/**
 * A Producer which sends messages to the Amazon Web Service Rekognition
 * <a href="https://aws.amazon.com/rekognition/">AWS Rekognition</a>
 */
public class Rekognition2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Rekognition2Producer.class);

    private transient String rekognitionProducerToString;

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Rekognition2Producer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case associateFaces -> associateFaces(getEndpoint().getAwsRekognitionClient(), exchange);
            case compareFaces -> compareFaces(getEndpoint().getAwsRekognitionClient(), exchange);
            case createCollection -> createCollection(getEndpoint().getAwsRekognitionClient(), exchange);
            case createUser -> createUser(getEndpoint().getAwsRekognitionClient(), exchange);
            case deleteCollection -> deleteCollection(getEndpoint().getAwsRekognitionClient(), exchange);
            case deleteFaces -> deleteFaces(getEndpoint().getAwsRekognitionClient(), exchange);
            case deleteUser -> deleteUser(getEndpoint().getAwsRekognitionClient(), exchange);
            case describeCollection -> describeCollection(getEndpoint().getAwsRekognitionClient(), exchange);
            case detectFaces -> detectFaces(getEndpoint().getAwsRekognitionClient(), exchange);
            case detectLabels -> detectLabels(getEndpoint().getAwsRekognitionClient(), exchange);
            case detectModerationLabels -> detectModerationLabels(getEndpoint().getAwsRekognitionClient(), exchange);
            case detectProtectiveEquipment -> detectProtectiveEquipment(getEndpoint().getAwsRekognitionClient(), exchange);
            case detectText -> detectText(getEndpoint().getAwsRekognitionClient(), exchange);
            case disassociateFaces -> disassociateFaces(getEndpoint().getAwsRekognitionClient(), exchange);
            case getCelebrityInfo -> getCelebrityInfo(getEndpoint().getAwsRekognitionClient(), exchange);
            case getMediaAnalysisJob -> getMediaAnalysisJob(getEndpoint().getAwsRekognitionClient(), exchange);
            case indexFaces -> indexFaces(getEndpoint().getAwsRekognitionClient(), exchange);
            case listCollections -> listCollections(getEndpoint().getAwsRekognitionClient(), exchange);
            case listMediaAnalysisJobs -> listMediaAnalysisJobs(getEndpoint().getAwsRekognitionClient(), exchange);
            case listFaces -> listFaces(getEndpoint().getAwsRekognitionClient(), exchange);
            case listUsers -> listUsers(getEndpoint().getAwsRekognitionClient(), exchange);
            case recognizeCelebrities -> recognizeCelebrities(getEndpoint().getAwsRekognitionClient(), exchange);
            case searchFaces -> searchFaces(getEndpoint().getAwsRekognitionClient(), exchange);
            case searchFacesByImage -> searchFacesByImage(getEndpoint().getAwsRekognitionClient(), exchange);
            case searchUsers -> searchUsers(getEndpoint().getAwsRekognitionClient(), exchange);
            case searchUsersByImage -> searchUsersByImage(getEndpoint().getAwsRekognitionClient(), exchange);
            case startMediaAnalysisJob -> startMediaAnalysisJob(getEndpoint().getAwsRekognitionClient(), exchange);
            default -> throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private Rekognition2Operations determineOperation(Exchange exchange) {
        Rekognition2Operations operation
                = exchange.getIn().getHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected Rekognition2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (rekognitionProducerToString == null) {
            rekognitionProducerToString = "RekognitionProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return rekognitionProducerToString;
    }

    @Override
    public Rekognition2Endpoint getEndpoint() {
        return (Rekognition2Endpoint) super.getEndpoint();
    }

    private void associateFaces(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof AssociateFacesRequest request) {
                AssociateFacesResponse result;
                try {
                    result = rekognitionClient.associateFaces(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Associate Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            AssociateFacesRequest.Builder builder = AssociateFacesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.USER_ID))) {
                String userId = exchange.getIn().getHeader(Rekognition2Constants.USER_ID, String.class);
                builder.userId(userId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FACE_IDS))) {
                Collection<String> faceIds = exchange.getIn().getHeader(Rekognition2Constants.FACE_IDS, Collection.class);
                builder.faceIds(faceIds);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.USER_MATCH_THRESHOLD))) {
                Float userMatchThreshold = exchange.getIn().getHeader(Rekognition2Constants.USER_MATCH_THRESHOLD, Float.class);
                builder.userMatchThreshold(userMatchThreshold);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN))) {
                String clientRequestToken
                        = exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN, String.class);
                builder.clientRequestToken(clientRequestToken);
            }
            AssociateFacesResponse result;
            try {
                result = rekognitionClient.associateFaces(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Associate Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void compareFaces(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CompareFacesRequest request) {
                CompareFacesResponse result;
                try {
                    result = rekognitionClient.compareFaces(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Compare Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CompareFacesRequest.Builder builder = CompareFacesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.SOURCE_IMAGE))) {
                Image sourceImage = exchange.getIn().getHeader(Rekognition2Constants.SOURCE_IMAGE, Image.class);
                builder.sourceImage(sourceImage);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.TARGET_IMAGE))) {
                Image targetImage = exchange.getIn().getHeader(Rekognition2Constants.TARGET_IMAGE, Image.class);
                builder.targetImage(targetImage);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.SIMILARITY_THRESHOLD))) {
                Float similarityThreshold = exchange.getIn().getHeader(Rekognition2Constants.SIMILARITY_THRESHOLD, Float.class);
                builder.similarityThreshold(similarityThreshold);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.QUALITY_FILTER))) {
                String qualityFilter = exchange.getIn().getHeader(Rekognition2Constants.QUALITY_FILTER, String.class);
                builder.qualityFilter(qualityFilter);
            }
            CompareFacesResponse result;
            try {
                result = rekognitionClient.compareFaces(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Compare Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createCollection(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateCollectionRequest request) {
                CreateCollectionResponse result;
                try {
                    result = rekognitionClient.createCollection(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Collection command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateCollectionRequest.Builder builder = CreateCollectionRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            CreateCollectionResponse result;
            try {
                result = rekognitionClient.createCollection(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Collection command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createUser(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateUserRequest request) {
                CreateUserResponse result;
                try {
                    result = rekognitionClient.createUser(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create User command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateUserRequest.Builder builder = CreateUserRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.USER_ID))) {
                String userId = exchange.getIn().getHeader(Rekognition2Constants.USER_ID, String.class);
                builder.userId(userId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN))) {
                String clientRequestToken
                        = exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN, String.class);
                builder.clientRequestToken(clientRequestToken);
            }
            CreateUserResponse result;
            try {
                result = rekognitionClient.createUser(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create User command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteCollection(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteCollectionRequest request) {
                DeleteCollectionResponse result;
                try {
                    result = rekognitionClient.deleteCollection(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Collection command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteCollectionRequest.Builder builder = DeleteCollectionRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            DeleteCollectionResponse result;
            try {
                result = rekognitionClient.deleteCollection(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Collection command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteFaces(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteFacesRequest request) {
                DeleteFacesResponse result;
                try {
                    result = rekognitionClient.deleteFaces(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteFacesRequest.Builder builder = DeleteFacesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FACE_IDS))) {
                Collection<String> faceIds = exchange.getIn().getHeader(Rekognition2Constants.FACE_IDS, Collection.class);
                builder.faceIds(faceIds);
            }
            DeleteFacesResponse result;
            try {
                result = rekognitionClient.deleteFaces(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteUser(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteUserRequest request) {
                DeleteUserResponse result;
                try {
                    result = rekognitionClient.deleteUser(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete User command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteUserRequest.Builder builder = DeleteUserRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.USER_ID))) {
                String userId = exchange.getIn().getHeader(Rekognition2Constants.USER_ID, String.class);
                builder.userId(userId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN))) {
                String clientRequestToken
                        = exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN, String.class);
                builder.clientRequestToken(clientRequestToken);
            }
            DeleteUserResponse result;
            try {
                result = rekognitionClient.deleteUser(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete User command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeCollection(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeCollectionRequest request) {
                DescribeCollectionResponse result;
                try {
                    result = rekognitionClient.describeCollection(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Collection command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeCollectionRequest.Builder builder = DescribeCollectionRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            DescribeCollectionResponse result;
            try {
                result = rekognitionClient.describeCollection(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Collection command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void detectFaces(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectFacesRequest request) {
                DetectFacesResponse result;
                try {
                    result = rekognitionClient.detectFaces(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DetectFacesRequest.Builder builder = DetectFacesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.IMAGE))) {
                Image image = exchange.getIn().getHeader(Rekognition2Constants.IMAGE, Image.class);
                builder.image(image);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FACIAL_ATTRIBUTES))) {
                List<Attribute> attributes = exchange.getIn().getHeader(Rekognition2Constants.FACIAL_ATTRIBUTES, List.class);
                builder.attributes(attributes);
            }
            DetectFacesResponse result;
            try {
                result = rekognitionClient.detectFaces(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void detectLabels(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectLabelsRequest request) {
                DetectLabelsResponse result;
                try {
                    result = rekognitionClient.detectLabels(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Labels command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DetectLabelsRequest.Builder builder = DetectLabelsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.IMAGE))) {
                Image image = exchange.getIn().getHeader(Rekognition2Constants.IMAGE, Image.class);
                builder.image(image);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_LABELS))) {
                Integer maxLabels = exchange.getIn().getHeader(Rekognition2Constants.MAX_LABELS, Integer.class);
                builder.maxLabels(maxLabels);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MIN_CONFIDENCE))) {
                Float minConfidence = exchange.getIn().getHeader(Rekognition2Constants.MIN_CONFIDENCE, Float.class);
                builder.minConfidence(minConfidence);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FEATURES))) {
                Collection<DetectLabelsFeatureName> features
                        = exchange.getIn().getHeader(Rekognition2Constants.FEATURES, Collection.class);
                builder.features(features);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.DETECT_LABELS_SETTINGS))) {
                DetectLabelsSettings detectLabelsSettings
                        = exchange.getIn().getHeader(Rekognition2Constants.DETECT_LABELS_SETTINGS, DetectLabelsSettings.class);
                builder.settings(detectLabelsSettings);
            }
            DetectLabelsResponse result;
            try {
                result = rekognitionClient.detectLabels(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Labels command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void detectModerationLabels(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectModerationLabelsRequest request) {
                DetectModerationLabelsResponse result;
                try {
                    result = rekognitionClient.detectModerationLabels(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Moderation Labels command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DetectModerationLabelsRequest.Builder builder = DetectModerationLabelsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.IMAGE))) {
                Image image = exchange.getIn().getHeader(Rekognition2Constants.IMAGE, Image.class);
                builder.image(image);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.HUMAN_LOOP_CONFIG))) {
                HumanLoopConfig humanLoopConfig
                        = exchange.getIn().getHeader(Rekognition2Constants.HUMAN_LOOP_CONFIG, HumanLoopConfig.class);
                builder.humanLoopConfig(humanLoopConfig);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MIN_CONFIDENCE))) {
                Float minConfidence = exchange.getIn().getHeader(Rekognition2Constants.MIN_CONFIDENCE, Float.class);
                builder.minConfidence(minConfidence);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.PROJECT_VERSION))) {
                String projectVersion = exchange.getIn().getHeader(Rekognition2Constants.PROJECT_VERSION, String.class);
                builder.projectVersion(projectVersion);
            }
            DetectModerationLabelsResponse result;
            try {
                result = rekognitionClient.detectModerationLabels(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Moderation Labels command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void detectProtectiveEquipment(RekognitionClient rekognitionClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectProtectiveEquipmentRequest request) {
                DetectProtectiveEquipmentResponse result;
                try {
                    result = rekognitionClient.detectProtectiveEquipment(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Protective Equipment command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DetectProtectiveEquipmentRequest.Builder builder = DetectProtectiveEquipmentRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.IMAGE))) {
                Image image = exchange.getIn().getHeader(Rekognition2Constants.IMAGE, Image.class);
                builder.image(image);
            }
            if (ObjectHelper.isNotEmpty(
                    exchange.getIn().getHeader(Rekognition2Constants.PROTECTIVE_EQUIPMENT_SUMMARIZATION_ATTRIBUTES))) {
                ProtectiveEquipmentSummarizationAttributes protectiveEquipmentSummarizationAttributes
                        = exchange.getIn().getHeader(Rekognition2Constants.PROTECTIVE_EQUIPMENT_SUMMARIZATION_ATTRIBUTES,
                                ProtectiveEquipmentSummarizationAttributes.class);
                builder.summarizationAttributes(protectiveEquipmentSummarizationAttributes);
            }
            DetectProtectiveEquipmentResponse result;
            try {
                result = rekognitionClient.detectProtectiveEquipment(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Protective Equipment command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void detectText(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectTextRequest request) {
                DetectTextResponse result;
                try {
                    result = rekognitionClient.detectText(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Text command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DetectTextRequest.Builder builder = DetectTextRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.IMAGE))) {
                Image image = exchange.getIn().getHeader(Rekognition2Constants.IMAGE, Image.class);
                builder.image(image);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.WORD_FILTER))) {
                DetectTextFilters filters
                        = exchange.getIn().getHeader(Rekognition2Constants.WORD_FILTER, DetectTextFilters.class);
                builder.filters(filters);
            }
            DetectTextResponse result;
            try {
                result = rekognitionClient.detectText(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Text command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void disassociateFaces(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DisassociateFacesRequest request) {
                DisassociateFacesResponse result;
                try {
                    result = rekognitionClient.disassociateFaces(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Disassociate Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DisassociateFacesRequest.Builder builder = DisassociateFacesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.USER_ID))) {
                String userId = exchange.getIn().getHeader(Rekognition2Constants.USER_ID, String.class);
                builder.userId(userId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FACE_IDS))) {
                Collection<String> faceIds = exchange.getIn().getHeader(Rekognition2Constants.FACE_IDS, Collection.class);
                builder.faceIds(faceIds);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN))) {
                String clientRequestToken
                        = exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN, String.class);
                builder.clientRequestToken(clientRequestToken);
            }
            DisassociateFacesResponse result;
            try {
                result = rekognitionClient.disassociateFaces(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Disassociate Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getCelebrityInfo(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetCelebrityInfoRequest request) {
                GetCelebrityInfoResponse result;
                try {
                    result = rekognitionClient.getCelebrityInfo(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Celebrity Info command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetCelebrityInfoRequest.Builder builder = GetCelebrityInfoRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.CELEBRITY_ID))) {
                String celebrityId = exchange.getIn().getHeader(Rekognition2Constants.CELEBRITY_ID, String.class);
                builder.id(celebrityId);
            }
            GetCelebrityInfoResponse result;
            try {
                result = rekognitionClient.getCelebrityInfo(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Celebrity Info command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getMediaAnalysisJob(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetMediaAnalysisJobRequest request) {
                GetMediaAnalysisJobResponse result;
                try {
                    result = rekognitionClient.getMediaAnalysisJob(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Media Analysis Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetMediaAnalysisJobRequest.Builder builder = GetMediaAnalysisJobRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.JOB_ID))) {
                String jobId = exchange.getIn().getHeader(Rekognition2Constants.JOB_ID, String.class);
                builder.jobId(jobId);
            }
            GetMediaAnalysisJobResponse result;
            try {
                result = rekognitionClient.getMediaAnalysisJob(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Media Analysis Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void indexFaces(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof IndexFacesRequest request) {
                IndexFacesResponse result;
                try {
                    result = rekognitionClient.indexFaces(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Index Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            IndexFacesRequest.Builder builder = IndexFacesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.IMAGE))) {
                Image image = exchange.getIn().getHeader(Rekognition2Constants.IMAGE, Image.class);
                builder.image(image);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.EXTERNAL_IMAGE_ID))) {
                String externalImageId = exchange.getIn().getHeader(Rekognition2Constants.EXTERNAL_IMAGE_ID, String.class);
                builder.externalImageId(externalImageId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.DETECTION_ATTRIBUTES))) {
                Collection<Attribute> detectionAttributes
                        = exchange.getIn().getHeader(Rekognition2Constants.DETECTION_ATTRIBUTES, Collection.class);
                builder.detectionAttributes(detectionAttributes);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_FACES))) {
                Integer maxFaces = exchange.getIn().getHeader(Rekognition2Constants.MAX_FACES, Integer.class);
                builder.maxFaces(maxFaces);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.QUALITY_FILTER))) {
                String qualityFilter = exchange.getIn().getHeader(Rekognition2Constants.QUALITY_FILTER, String.class);
                builder.qualityFilter(qualityFilter);
            }
            IndexFacesResponse result;
            try {
                result = rekognitionClient.indexFaces(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Index Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listCollections(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListCollectionsRequest request) {
                ListCollectionsResponse result;
                try {
                    result = rekognitionClient.listCollections(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Collections command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListCollectionsRequest.Builder builder = ListCollectionsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(Rekognition2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.NEXT_TOKEN))) {
                String nextToken = exchange.getIn().getHeader(Rekognition2Constants.NEXT_TOKEN, String.class);
                builder.nextToken(nextToken);
            }
            ListCollectionsResponse result;
            try {
                result = rekognitionClient.listCollections(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Collections command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listMediaAnalysisJobs(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListMediaAnalysisJobsRequest request) {
                ListMediaAnalysisJobsResponse result;
                try {
                    result = rekognitionClient.listMediaAnalysisJobs(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Media Analysis Jobs command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListMediaAnalysisJobsRequest.Builder builder = ListMediaAnalysisJobsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(Rekognition2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.NEXT_TOKEN))) {
                String nextToken = exchange.getIn().getHeader(Rekognition2Constants.NEXT_TOKEN, String.class);
                builder.nextToken(nextToken);
            }
            ListMediaAnalysisJobsResponse result;
            try {
                result = rekognitionClient.listMediaAnalysisJobs(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Media Analysis Jobs command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listFaces(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListFacesRequest request) {
                ListFacesResponse result;
                try {
                    result = rekognitionClient.listFaces(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListFacesRequest.Builder builder = ListFacesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(Rekognition2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.NEXT_TOKEN))) {
                String nextToken = exchange.getIn().getHeader(Rekognition2Constants.NEXT_TOKEN, String.class);
                builder.nextToken(nextToken);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.USER_ID))) {
                String userId = exchange.getIn().getHeader(Rekognition2Constants.USER_ID, String.class);
                builder.userId(userId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FACE_IDS))) {
                Collection<String> faceIds = exchange.getIn().getHeader(Rekognition2Constants.FACE_IDS, Collection.class);
                builder.faceIds(faceIds);
            }
            ListFacesResponse result;
            try {
                result = rekognitionClient.listFaces(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listUsers(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListUsersRequest request) {
                ListUsersResponse result;
                try {
                    result = rekognitionClient.listUsers(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Users command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListUsersRequest.Builder builder = ListUsersRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(Rekognition2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.NEXT_TOKEN))) {
                String nextToken = exchange.getIn().getHeader(Rekognition2Constants.NEXT_TOKEN, String.class);
                builder.nextToken(nextToken);
            }
            ListUsersResponse result;
            try {
                result = rekognitionClient.listUsers(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Users command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void recognizeCelebrities(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RecognizeCelebritiesRequest request) {
                RecognizeCelebritiesResponse result;
                try {
                    result = rekognitionClient.recognizeCelebrities(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Recognize Celebrities command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            RecognizeCelebritiesRequest.Builder builder = RecognizeCelebritiesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.IMAGE))) {
                Image image = exchange.getIn().getHeader(Rekognition2Constants.IMAGE, Image.class);
                builder.image(image);
            }
            RecognizeCelebritiesResponse result;
            try {
                result = rekognitionClient.recognizeCelebrities(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Recognize Celebrities command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void searchFaces(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof SearchFacesRequest request) {
                SearchFacesResponse result;
                try {
                    result = rekognitionClient.searchFaces(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Search Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            SearchFacesRequest.Builder builder = SearchFacesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FACE_ID))) {
                String faceId = exchange.getIn().getHeader(Rekognition2Constants.FACE_ID, String.class);
                builder.faceId(faceId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_FACES))) {
                Integer maxFaces = exchange.getIn().getHeader(Rekognition2Constants.MAX_FACES, Integer.class);
                builder.maxFaces(maxFaces);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FACE_MATCH_THRESHOLD))) {
                Float faceMatchThreshold = exchange.getIn().getHeader(Rekognition2Constants.FACE_MATCH_THRESHOLD, Float.class);
                builder.faceMatchThreshold(faceMatchThreshold);
            }
            SearchFacesResponse result;
            try {
                result = rekognitionClient.searchFaces(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Search Faces command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void searchFacesByImage(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof SearchFacesByImageRequest request) {
                SearchFacesByImageResponse result;
                try {
                    result = rekognitionClient.searchFacesByImage(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Search Faces By Image command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            SearchFacesByImageRequest.Builder builder = SearchFacesByImageRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.IMAGE))) {
                Image image = exchange.getIn().getHeader(Rekognition2Constants.IMAGE, Image.class);
                builder.image(image);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_FACES))) {
                Integer maxFaces = exchange.getIn().getHeader(Rekognition2Constants.MAX_FACES, Integer.class);
                builder.maxFaces(maxFaces);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FACE_MATCH_THRESHOLD))) {
                Float faceMatchThreshold = exchange.getIn().getHeader(Rekognition2Constants.FACE_MATCH_THRESHOLD, Float.class);
                builder.faceMatchThreshold(faceMatchThreshold);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.QUALITY_FILTER))) {
                String qualityFilter = exchange.getIn().getHeader(Rekognition2Constants.QUALITY_FILTER, String.class);
                builder.qualityFilter(qualityFilter);
            }
            SearchFacesByImageResponse result;
            try {
                result = rekognitionClient.searchFacesByImage(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Search Faces By Image command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void searchUsers(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof SearchUsersRequest request) {
                SearchUsersResponse result;
                try {
                    result = rekognitionClient.searchUsers(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Search Users command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            SearchUsersRequest.Builder builder = SearchUsersRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.USER_ID))) {
                String userId = exchange.getIn().getHeader(Rekognition2Constants.USER_ID, String.class);
                builder.userId(userId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.FACE_ID))) {
                String faceId = exchange.getIn().getHeader(Rekognition2Constants.FACE_ID, String.class);
                builder.faceId(faceId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_USERS))) {
                Integer maxUsers = exchange.getIn().getHeader(Rekognition2Constants.MAX_USERS, Integer.class);
                builder.maxUsers(maxUsers);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.USER_MATCH_THRESHOLD))) {
                Float userMatchThreshold = exchange.getIn().getHeader(Rekognition2Constants.USER_MATCH_THRESHOLD, Float.class);
                builder.userMatchThreshold(userMatchThreshold);
            }
            SearchUsersResponse result;
            try {
                result = rekognitionClient.searchUsers(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Search Users command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void searchUsersByImage(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof SearchUsersByImageRequest request) {
                SearchUsersByImageResponse result;
                try {
                    result = rekognitionClient.searchUsersByImage(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Search Users By Image command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            SearchUsersByImageRequest.Builder builder = SearchUsersByImageRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID))) {
                String collectionId = exchange.getIn().getHeader(Rekognition2Constants.COLLECTION_ID, String.class);
                builder.collectionId(collectionId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.IMAGE))) {
                Image image = exchange.getIn().getHeader(Rekognition2Constants.IMAGE, Image.class);
                builder.image(image);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.MAX_USERS))) {
                Integer maxUsers = exchange.getIn().getHeader(Rekognition2Constants.MAX_USERS, Integer.class);
                builder.maxUsers(maxUsers);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.USER_MATCH_THRESHOLD))) {
                Float userMatchThreshold = exchange.getIn().getHeader(Rekognition2Constants.USER_MATCH_THRESHOLD, Float.class);
                builder.userMatchThreshold(userMatchThreshold);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.QUALITY_FILTER))) {
                String qualityFilter = exchange.getIn().getHeader(Rekognition2Constants.QUALITY_FILTER, String.class);
                builder.qualityFilter(qualityFilter);
            }
            SearchUsersByImageResponse result;
            try {
                result = rekognitionClient.searchUsersByImage(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Search Users By Image command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void startMediaAnalysisJob(RekognitionClient rekognitionClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartMediaAnalysisJobRequest request) {
                StartMediaAnalysisJobResponse result;
                try {
                    result = rekognitionClient.startMediaAnalysisJob(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Media Analysis Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StartMediaAnalysisJobRequest.Builder builder = StartMediaAnalysisJobRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.JOB_NAME))) {
                String jobName = exchange.getIn().getHeader(Rekognition2Constants.JOB_NAME, String.class);
                builder.jobName(jobName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.INPUT))) {
                MediaAnalysisInput input = exchange.getIn().getHeader(Rekognition2Constants.INPUT, MediaAnalysisInput.class);
                builder.input(input);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.OUTPUT_CONFIG))) {
                MediaAnalysisOutputConfig outputConfig
                        = exchange.getIn().getHeader(Rekognition2Constants.OUTPUT_CONFIG, MediaAnalysisOutputConfig.class);
                builder.outputConfig(outputConfig);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.OPERATIONS_CONFIG))) {
                MediaAnalysisOperationsConfig operationsConfig = exchange.getIn()
                        .getHeader(Rekognition2Constants.OPERATIONS_CONFIG, MediaAnalysisOperationsConfig.class);
                builder.operationsConfig(operationsConfig);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN))) {
                String clientRequestToken
                        = exchange.getIn().getHeader(Rekognition2Constants.CLIENT_REQUEST_TOKEN, String.class);
                builder.clientRequestToken(clientRequestToken);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Rekognition2Constants.KMS_KEY_ID))) {
                String kmsKeyId = exchange.getIn().getHeader(Rekognition2Constants.KMS_KEY_ID, String.class);
                builder.kmsKeyId(kmsKeyId);
            }
            StartMediaAnalysisJobResponse result;
            try {
                result = rekognitionClient.startMediaAnalysisJob(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Start Media Analysis Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new Rekognition2ProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
