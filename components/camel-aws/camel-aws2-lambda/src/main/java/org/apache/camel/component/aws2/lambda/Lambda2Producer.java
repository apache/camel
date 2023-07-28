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
package org.apache.camel.component.aws2.lambda;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateAliasRequest;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeadLetterConfig;
import software.amazon.awssdk.services.lambda.model.DeleteAliasRequest;
import software.amazon.awssdk.services.lambda.model.DeleteAliasResponse;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.GetAliasRequest;
import software.amazon.awssdk.services.lambda.model.GetAliasResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.ListAliasesRequest;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsRequest;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.ListTagsRequest;
import software.amazon.awssdk.services.lambda.model.ListTagsResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionRequest;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.TagResourceRequest;
import software.amazon.awssdk.services.lambda.model.TagResourceResponse;
import software.amazon.awssdk.services.lambda.model.TracingConfig;
import software.amazon.awssdk.services.lambda.model.UntagResourceRequest;
import software.amazon.awssdk.services.lambda.model.UntagResourceResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.lambda.model.VpcConfig;

/**
 * A Producer which sends messages to the Amazon Web Service Lambda <a href="https://aws.amazon.com/lambda/">AWS
 * Lambda</a>
 */
public class Lambda2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Lambda2Producer.class);

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Lambda2Producer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case getFunction:
                getFunction(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case createFunction:
                createFunction(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case deleteFunction:
                deleteFunction(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case invokeFunction:
                invokeFunction(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case listFunctions:
                listFunctions(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case updateFunction:
                updateFunction(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case createEventSourceMapping:
                createEventSourceMapping(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case deleteEventSourceMapping:
                deleteEventSourceMapping(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case listEventSourceMapping:
                listEventSourceMapping(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case listTags:
                listTags(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case tagResource:
                tagResource(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case untagResource:
                untagResource(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case publishVersion:
                publishVersion(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case listVersions:
                listVersions(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case createAlias:
                createAlias(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case deleteAlias:
                deleteAlias(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case getAlias:
                getAlias(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            case listAliases:
                listAliases(getEndpoint().getAwsLambdaClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void getFunction(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        GetFunctionRequest request = null;
        GetFunctionResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(GetFunctionRequest.class);
        } else {
            GetFunctionRequest.Builder builder = GetFunctionRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            request = builder.build();
        }
        try {
            result = lambdaClient
                    .getFunction(request);
        } catch (AwsServiceException ase) {
            LOG.trace("getFunction command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteFunction(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        DeleteFunctionRequest request = null;
        DeleteFunctionResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(DeleteFunctionRequest.class);
        } else {
            DeleteFunctionRequest.Builder builder = DeleteFunctionRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            request = builder.build();
        }
        try {
            result = lambdaClient
                    .deleteFunction(request);
        } catch (AwsServiceException ase) {
            LOG.trace("deleteFunction command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listFunctions(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        ListFunctionsRequest request = null;
        ListFunctionsResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(ListFunctionsRequest.class);
        } else {
            ListFunctionsRequest.Builder builder = ListFunctionsRequest.builder();
            request = builder.build();
        }
        try {
            result = lambdaClient.listFunctions(request);
        } catch (AwsServiceException ase) {
            LOG.trace("listFunctions command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void invokeFunction(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        InvokeRequest request = null;
        InvokeResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(InvokeRequest.class);
        } else {
            InvokeRequest.Builder builder = InvokeRequest.builder();
            request = builder.functionName(getEndpoint().getFunction())
                    .payload(SdkBytes.fromString(exchange.getIn().getBody(String.class), Charset.defaultCharset())).build();
        }
        try {
            result = lambdaClient.invoke(request);
        } catch (AwsServiceException ase) {
            LOG.trace("invokeFunction command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result.payload().asUtf8String());
    }

    @SuppressWarnings("unchecked")
    private void createFunction(LambdaClient lambdaClient, Exchange exchange) throws Exception {
        CreateFunctionRequest request = null;
        CreateFunctionResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(CreateFunctionRequest.class);
        } else {
            CreateFunctionRequest.Builder builder = CreateFunctionRequest.builder();
            builder.functionName(getEndpoint().getFunction());

            FunctionCode.Builder functionCode = FunctionCode.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_BUCKET))) {
                String s3Bucket = exchange.getIn().getHeader(Lambda2Constants.S3_BUCKET, String.class);
                functionCode.s3Bucket(s3Bucket);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_KEY))) {
                String s3Key = exchange.getIn().getHeader(Lambda2Constants.S3_KEY, String.class);
                functionCode.s3Key(s3Key);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_OBJECT_VERSION))) {
                String s3ObjectVersion = exchange.getIn().getHeader(Lambda2Constants.S3_OBJECT_VERSION, String.class);
                functionCode.s3ObjectVersion(s3ObjectVersion);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.ZIP_FILE))) {
                String zipFile = exchange.getIn().getHeader(Lambda2Constants.ZIP_FILE, String.class);
                File fileLocalPath = new File(zipFile);
                try (FileInputStream inputStream = new FileInputStream(fileLocalPath)) {
                    functionCode.zipFile(SdkBytes.fromInputStream(inputStream));
                }
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getBody())) {
                functionCode.zipFile(SdkBytes.fromByteBuffer(exchange.getIn().getBody(ByteBuffer.class)));
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getBody())
                    || ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.ZIP_FILE))
                    || ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_BUCKET))
                            && ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_KEY))) {
                builder.code(functionCode.build());
            } else {
                throw new IllegalArgumentException("At least S3 bucket/S3 key or zip file must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.ROLE))) {
                builder.role(exchange.getIn().getHeader(Lambda2Constants.ROLE, String.class));
            } else {
                throw new IllegalArgumentException("Role must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RUNTIME))) {
                builder.runtime(exchange.getIn().getHeader(Lambda2Constants.RUNTIME, String.class));
            } else {
                throw new IllegalArgumentException("Runtime must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.HANDLER))) {
                builder.handler(exchange.getIn().getHeader(Lambda2Constants.HANDLER, String.class));
            } else {
                throw new IllegalArgumentException("Handler must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.DESCRIPTION))) {
                String description = exchange.getIn().getHeader(Lambda2Constants.DESCRIPTION, String.class);
                builder.description(description);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.TARGET_ARN))) {
                String targetArn = exchange.getIn().getHeader(Lambda2Constants.TARGET_ARN, String.class);
                builder.deadLetterConfig(DeadLetterConfig.builder().targetArn(targetArn).build());
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.MEMORY_SIZE))) {
                Integer memorySize = exchange.getIn().getHeader(Lambda2Constants.MEMORY_SIZE, Integer.class);
                builder.memorySize(memorySize);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.KMS_KEY_ARN))) {
                String kmsKeyARN = exchange.getIn().getHeader(Lambda2Constants.KMS_KEY_ARN, String.class);
                builder.kmsKeyArn(kmsKeyARN);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.PUBLISH))) {
                Boolean publish = exchange.getIn().getHeader(Lambda2Constants.PUBLISH, Boolean.class);
                builder.publish(publish);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(Lambda2Constants.TIMEOUT, Integer.class);
                builder.timeout(timeout);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.TRACING_CONFIG))) {
                String tracingConfigMode = exchange.getIn().getHeader(Lambda2Constants.TRACING_CONFIG, String.class);
                builder.tracingConfig(TracingConfig.builder().mode(tracingConfigMode).build());
            }

            Map<String, String> environmentVariables
                    = CastUtils.cast(exchange.getIn().getHeader(Lambda2Constants.ENVIRONMENT_VARIABLES, Map.class));
            if (environmentVariables != null) {
                builder.environment(Environment.builder().variables(environmentVariables).build());
            }

            Map<String, String> tags = CastUtils.cast(exchange.getIn().getHeader(Lambda2Constants.TAGS, Map.class));
            if (tags != null) {
                builder.tags(tags);
            }

            List<String> securityGroupIds = CastUtils.cast(exchange.getIn().getHeader(Lambda2Constants.SECURITY_GROUP_IDS,
                    (Class<List<String>>) (Object) List.class));
            List<String> subnetIds = CastUtils.cast(
                    exchange.getIn().getHeader(Lambda2Constants.SUBNET_IDS, (Class<List<String>>) (Object) List.class));
            if (securityGroupIds != null || subnetIds != null) {
                VpcConfig.Builder vpcConfig = VpcConfig.builder();
                if (securityGroupIds != null) {
                    vpcConfig.securityGroupIds(securityGroupIds);
                }
                if (subnetIds != null) {
                    vpcConfig.subnetIds(subnetIds);
                }
                builder.vpcConfig(vpcConfig.build());
            }

            request = builder.build();
        }
        try {
            result = lambdaClient.createFunction(request);
        } catch (AwsServiceException ase) {
            LOG.trace("createFunction command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void updateFunction(LambdaClient lambdaClient, Exchange exchange) throws Exception {
        UpdateFunctionCodeRequest request = null;
        UpdateFunctionCodeResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(UpdateFunctionCodeRequest.class);
        } else {
            UpdateFunctionCodeRequest.Builder builder = UpdateFunctionCodeRequest.builder();
            builder.functionName(getEndpoint().getFunction());

            if (ObjectHelper.isEmpty(exchange.getIn().getBody())
                    && ObjectHelper.isEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_BUCKET))
                    && ObjectHelper.isEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_KEY))) {
                throw new IllegalArgumentException("At least S3 bucket/S3 key or zip file must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.PUBLISH))) {
                Boolean publish = exchange.getIn().getHeader(Lambda2Constants.PUBLISH, Boolean.class);
                builder.publish(publish);
            }

            request = builder.build();
        }
        try {
            result = lambdaClient.updateFunctionCode(request);

        } catch (AwsServiceException ase) {
            LOG.trace("updateFunction command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createEventSourceMapping(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        CreateEventSourceMappingRequest request = null;
        CreateEventSourceMappingResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(CreateEventSourceMappingRequest.class);
        } else {
            CreateEventSourceMappingRequest.Builder builder = CreateEventSourceMappingRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_ARN))) {
                builder.eventSourceArn(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_ARN, String.class));
            } else {
                throw new IllegalArgumentException("Event Source Arn must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_BATCH_SIZE))) {
                Integer batchSize = exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_BATCH_SIZE, Integer.class);
                builder.batchSize(batchSize);
            }
            request = builder.build();
        }
        try {
            result = lambdaClient.createEventSourceMapping(request);
        } catch (AwsServiceException ase) {
            LOG.trace("createEventSourceMapping command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteEventSourceMapping(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        DeleteEventSourceMappingRequest request = null;
        DeleteEventSourceMappingResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(DeleteEventSourceMappingRequest.class);
        } else {
            DeleteEventSourceMappingRequest.Builder builder = DeleteEventSourceMappingRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_UUID))) {
                builder.uuid(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_UUID, String.class));
            } else {
                throw new IllegalArgumentException("Event Source Arn must be specified");
            }
            request = builder.build();
        }

        try {
            result = lambdaClient.deleteEventSourceMapping(request);
        } catch (AwsServiceException ase) {
            LOG.trace("deleteEventSourceMapping command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listEventSourceMapping(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        ListEventSourceMappingsRequest request = null;
        ListEventSourceMappingsResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(ListEventSourceMappingsRequest.class);
        } else {
            ListEventSourceMappingsRequest.Builder builder = ListEventSourceMappingsRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            request = builder.build();
        }
        try {
            result = lambdaClient.listEventSourceMappings(request);
        } catch (AwsServiceException ase) {
            LOG.trace("listEventSourceMapping command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listTags(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        ListTagsRequest request = null;
        ListTagsResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(ListTagsRequest.class);
        } else {
            ListTagsRequest.Builder builder = ListTagsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN))) {
                String resource = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN, String.class);
                builder.resource(resource);
            } else {
                throw new IllegalArgumentException("The resource ARN must be specified");
            }
            request = builder.build();
        }
        try {
            result = lambdaClient.listTags(request);
        } catch (AwsServiceException ase) {
            LOG.trace("listTags command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    @SuppressWarnings("unchecked")
    private void tagResource(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        TagResourceRequest request = null;
        TagResourceResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(TagResourceRequest.class);
        } else {
            TagResourceRequest.Builder builder = TagResourceRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN))) {
                String resource = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN, String.class);
                builder.resource(resource);
            } else {
                throw new IllegalArgumentException("The resource ARN must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_TAGS))) {
                Map<String, String> tags = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_TAGS, Map.class);
                builder.tags(tags);
            } else {
                throw new IllegalArgumentException("The tags must be specified");
            }
            request = builder.build();
        }
        try {
            result = lambdaClient.tagResource(request);
        } catch (AwsServiceException ase) {
            LOG.trace("listTags command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    @SuppressWarnings("unchecked")
    private void untagResource(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        UntagResourceRequest request = null;
        UntagResourceResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(UntagResourceRequest.class);
        } else {
            UntagResourceRequest.Builder builder = UntagResourceRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN))) {
                String resource = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN, String.class);
                builder.resource(resource);
            } else {
                throw new IllegalArgumentException("The resource ARN must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_TAG_KEYS))) {
                List<String> tagKeys = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_TAG_KEYS, List.class);
                builder.tagKeys(tagKeys);
            } else {
                throw new IllegalArgumentException("The tag keys must be specified");
            }
            request = builder.build();
        }
        try {
            result = lambdaClient.untagResource(request);
        } catch (AwsServiceException ase) {
            LOG.trace("untagResource command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void publishVersion(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        PublishVersionRequest request = null;
        PublishVersionResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(PublishVersionRequest.class);
        } else {
            PublishVersionRequest.Builder builder = PublishVersionRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.VERSION_DESCRIPTION))) {
                String description = exchange.getIn().getHeader(Lambda2Constants.VERSION_DESCRIPTION, String.class);
                builder.description(description);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.VERSION_REVISION_ID))) {
                String revisionId = exchange.getIn().getHeader(Lambda2Constants.VERSION_REVISION_ID, String.class);
                builder.revisionId(revisionId);
            }
            request = builder.build();
        }
        try {
            result = lambdaClient.publishVersion(request);
        } catch (AwsServiceException ase) {
            LOG.trace("publishVersion command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listVersions(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        ListVersionsByFunctionRequest request = null;
        ListVersionsByFunctionResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(ListVersionsByFunctionRequest.class);
        } else {
            ListVersionsByFunctionRequest.Builder builder = ListVersionsByFunctionRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            request = builder.build();
        }
        try {
            result = lambdaClient.listVersionsByFunction(request);
        } catch (AwsServiceException ase) {
            LOG.trace("publishVersion command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createAlias(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        CreateAliasRequest request = null;
        CreateAliasResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(CreateAliasRequest.class);
        } else {
            CreateAliasRequest.Builder builder = CreateAliasRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            String version = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_VERSION, String.class);
            String aliasName = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, String.class);
            if (ObjectHelper.isEmpty(version) || ObjectHelper.isEmpty(aliasName)) {
                throw new IllegalArgumentException("Function Version and alias must be specified to create an alias");
            }
            builder.functionVersion(version);
            builder.name(aliasName);
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_DESCRIPTION))) {
                String aliasDescription
                        = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_DESCRIPTION, String.class);
                builder.description(aliasDescription);
            }
            request = builder.build();
        }
        try {
            result = lambdaClient.createAlias(request);
        } catch (AwsServiceException ase) {
            LOG.trace("createAlias command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteAlias(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        DeleteAliasRequest request = null;
        DeleteAliasResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(DeleteAliasRequest.class);
        } else {
            DeleteAliasRequest.Builder builder = DeleteAliasRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            String aliasName = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, String.class);
            if (ObjectHelper.isEmpty(aliasName)) {
                throw new IllegalArgumentException("Function alias must be specified to delete an alias");
            }
            builder.name(aliasName);
            request = builder.build();
        }
        try {
            result = lambdaClient.deleteAlias(request);
        } catch (AwsServiceException ase) {
            LOG.trace("deleteAlias command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void getAlias(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        GetAliasRequest request = null;
        GetAliasResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(GetAliasRequest.class);
        } else {
            GetAliasRequest.Builder builder = GetAliasRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            String aliasName = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, String.class);
            if (ObjectHelper.isEmpty(aliasName)) {
                throw new IllegalArgumentException("Function alias must be specified to get an alias");
            }
            builder.name(aliasName);
            request = builder.build();
        }
        try {
            result = lambdaClient.getAlias(request);
        } catch (AwsServiceException ase) {
            LOG.trace("getAlias command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listAliases(LambdaClient lambdaClient, Exchange exchange) throws InvalidPayloadException {
        ListAliasesRequest request = null;
        ListAliasesResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(ListAliasesRequest.class);
        } else {
            ListAliasesRequest.Builder builder = ListAliasesRequest.builder();
            builder.functionName(getEndpoint().getFunction());
            String version = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_VERSION, String.class);
            if (!ObjectHelper.isEmpty(version)) {
                builder.functionVersion(version);
            }
            request = builder.build();
        }
        try {
            result = lambdaClient.listAliases(request);
        } catch (AwsServiceException ase) {
            LOG.trace("listAliases command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private Lambda2Operations determineOperation(Exchange exchange) {
        Lambda2Operations operation = exchange.getIn().getHeader(Lambda2Constants.OPERATION, Lambda2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation() == null
                    ? Lambda2Operations.invokeFunction : getConfiguration().getOperation();
        }
        return operation;
    }

    protected Lambda2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public Lambda2Endpoint getEndpoint() {
        return (Lambda2Endpoint) super.getEndpoint();
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
            producerHealthCheck = new Lambda2ProducerHealthCheck(getEndpoint(), id);
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
