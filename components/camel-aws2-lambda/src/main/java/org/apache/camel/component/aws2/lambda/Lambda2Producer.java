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
import org.apache.camel.Message;
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
 * A Producer which sends messages to the Amazon Web Service Lambda
 * <a href="https://aws.amazon.com/lambda/">AWS Lambda</a>
 */
public class Lambda2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Lambda2Producer.class);

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

    private void getFunction(LambdaClient lambdaClient, Exchange exchange) {
        GetFunctionResponse result;
        try {
            result = lambdaClient.getFunction(GetFunctionRequest.builder().functionName(getEndpoint().getFunction()).build());
        } catch (AwsServiceException ase) {
            LOG.trace("getFunction command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteFunction(LambdaClient lambdaClient, Exchange exchange) {
        DeleteFunctionResponse result;
        try {
            result = lambdaClient.deleteFunction(DeleteFunctionRequest.builder().functionName(getEndpoint().getFunction()).build());
        } catch (AwsServiceException ase) {
            LOG.trace("deleteFunction command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listFunctions(LambdaClient lambdaClient, Exchange exchange) {
        ListFunctionsResponse result;
        try {
            result = lambdaClient.listFunctions();
        } catch (AwsServiceException ase) {
            LOG.trace("listFunctions command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void invokeFunction(LambdaClient lambdaClient, Exchange exchange) {
        InvokeResponse result;
        try {
            InvokeRequest request = InvokeRequest.builder().functionName(getEndpoint().getFunction())
                .payload(SdkBytes.fromString(exchange.getIn().getBody(String.class), Charset.defaultCharset())).build();
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
        CreateFunctionResponse result;

        try {
            CreateFunctionRequest.Builder request = CreateFunctionRequest.builder().functionName(getEndpoint().getFunction());

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

            if (ObjectHelper.isNotEmpty(exchange.getIn().getBody()) || (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_BUCKET))
                                                                        && ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_KEY)))) {
                request.code(functionCode.build());
            } else {
                throw new IllegalArgumentException("At least S3 bucket/S3 key or zip file must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.ROLE))) {
                request.role(exchange.getIn().getHeader(Lambda2Constants.ROLE, String.class));
            } else {
                throw new IllegalArgumentException("Role must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RUNTIME))) {
                request.runtime(exchange.getIn().getHeader(Lambda2Constants.RUNTIME, String.class));
            } else {
                throw new IllegalArgumentException("Runtime must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.HANDLER))) {
                request.handler(exchange.getIn().getHeader(Lambda2Constants.HANDLER, String.class));
            } else {
                throw new IllegalArgumentException("Handler must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.DESCRIPTION))) {
                String description = exchange.getIn().getHeader(Lambda2Constants.DESCRIPTION, String.class);
                request.description(description);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.TARGET_ARN))) {
                String targetArn = exchange.getIn().getHeader(Lambda2Constants.TARGET_ARN, String.class);
                request.deadLetterConfig(DeadLetterConfig.builder().targetArn(targetArn).build());
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.MEMORY_SIZE))) {
                Integer memorySize = exchange.getIn().getHeader(Lambda2Constants.MEMORY_SIZE, Integer.class);
                request.memorySize(memorySize);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.KMS_KEY_ARN))) {
                String kmsKeyARN = exchange.getIn().getHeader(Lambda2Constants.KMS_KEY_ARN, String.class);
                request.kmsKeyArn(kmsKeyARN);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.PUBLISH))) {
                Boolean publish = exchange.getIn().getHeader(Lambda2Constants.PUBLISH, Boolean.class);
                request.publish(publish);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(Lambda2Constants.TIMEOUT, Integer.class);
                request.timeout(timeout);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.TRACING_CONFIG))) {
                String tracingConfigMode = exchange.getIn().getHeader(Lambda2Constants.TRACING_CONFIG, String.class);
                request.tracingConfig(TracingConfig.builder().mode(tracingConfigMode).build());
            }

            Map<String, String> environmentVariables = CastUtils.cast(exchange.getIn().getHeader(Lambda2Constants.ENVIRONMENT_VARIABLES, Map.class));
            if (environmentVariables != null) {
                request.environment(Environment.builder().variables(environmentVariables).build());
            }

            Map<String, String> tags = CastUtils.cast(exchange.getIn().getHeader(Lambda2Constants.TAGS, Map.class));
            if (tags != null) {
                request.tags(tags);
            }

            List<String> securityGroupIds = CastUtils.cast(exchange.getIn().getHeader(Lambda2Constants.SECURITY_GROUP_IDS, (Class<List<String>>)(Object)List.class));
            List<String> subnetIds = CastUtils.cast(exchange.getIn().getHeader(Lambda2Constants.SUBNET_IDS, (Class<List<String>>)(Object)List.class));
            if (securityGroupIds != null || subnetIds != null) {
                VpcConfig.Builder vpcConfig = VpcConfig.builder();
                if (securityGroupIds != null) {
                    vpcConfig.securityGroupIds(securityGroupIds);
                }
                if (subnetIds != null) {
                    vpcConfig.subnetIds(subnetIds);
                }
                request.vpcConfig(vpcConfig.build());
            }
            result = lambdaClient.createFunction(request.build());

        } catch (AwsServiceException ase) {
            LOG.trace("createFunction command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void updateFunction(LambdaClient lambdaClient, Exchange exchange) throws Exception {
        UpdateFunctionCodeResponse result;

        try {
            UpdateFunctionCodeRequest.Builder request = UpdateFunctionCodeRequest.builder().functionName(getEndpoint().getFunction());

            if (ObjectHelper.isEmpty(exchange.getIn().getBody())
                && (ObjectHelper.isEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_BUCKET)) && ObjectHelper.isEmpty(exchange.getIn().getHeader(Lambda2Constants.S3_KEY)))) {
                throw new IllegalArgumentException("At least S3 bucket/S3 key or zip file must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.PUBLISH))) {
                Boolean publish = exchange.getIn().getHeader(Lambda2Constants.PUBLISH, Boolean.class);
                request.publish(publish);
            }

            result = lambdaClient.updateFunctionCode(request.build());

        } catch (AwsServiceException ase) {
            LOG.trace("updateFunction command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createEventSourceMapping(LambdaClient lambdaClient, Exchange exchange) {
        CreateEventSourceMappingResponse result;
        try {
            CreateEventSourceMappingRequest.Builder request = CreateEventSourceMappingRequest.builder().functionName(getEndpoint().getFunction());
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_ARN))) {
                request.eventSourceArn(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_ARN, String.class));
            } else {
                throw new IllegalArgumentException("Event Source Arn must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_BATCH_SIZE))) {
                Integer batchSize = exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_BATCH_SIZE, Integer.class);
                request.batchSize(batchSize);
            }
            result = lambdaClient.createEventSourceMapping(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("createEventSourceMapping command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteEventSourceMapping(LambdaClient lambdaClient, Exchange exchange) {
        DeleteEventSourceMappingResponse result;
        try {
            DeleteEventSourceMappingRequest.Builder request = DeleteEventSourceMappingRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_UUID))) {
                request.uuid(exchange.getIn().getHeader(Lambda2Constants.EVENT_SOURCE_UUID, String.class));
            } else {
                throw new IllegalArgumentException("Event Source Arn must be specified");
            }
            result = lambdaClient.deleteEventSourceMapping(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("deleteEventSourceMapping command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listEventSourceMapping(LambdaClient lambdaClient, Exchange exchange) {
        ListEventSourceMappingsResponse result;
        try {
            ListEventSourceMappingsRequest.Builder request = ListEventSourceMappingsRequest.builder().functionName(getEndpoint().getFunction());
            result = lambdaClient.listEventSourceMappings(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("listEventSourceMapping command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listTags(LambdaClient lambdaClient, Exchange exchange) {
        ListTagsResponse result;
        try {
            ListTagsRequest.Builder request = ListTagsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN))) {
                String resource = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN, String.class);
                request.resource(resource);
            } else {
                throw new IllegalArgumentException("The resource ARN must be specified");
            }
            result = lambdaClient.listTags(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("listTags command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    @SuppressWarnings("unchecked")
    private void tagResource(LambdaClient lambdaClient, Exchange exchange) {
        TagResourceResponse result;
        try {
            TagResourceRequest.Builder request = TagResourceRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN))) {
                String resource = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN, String.class);
                request.resource(resource);
            } else {
                throw new IllegalArgumentException("The resource ARN must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_TAGS))) {
                Map<String, String> tags = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_TAGS, Map.class);
                request.tags(tags);
            } else {
                throw new IllegalArgumentException("The tags must be specified");
            }
            result = lambdaClient.tagResource(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("listTags command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    @SuppressWarnings("unchecked")
    private void untagResource(LambdaClient lambdaClient, Exchange exchange) {
        UntagResourceResponse result;
        try {
            UntagResourceRequest.Builder request = UntagResourceRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN))) {
                String resource = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_ARN, String.class);
                request.resource(resource);
            } else {
                throw new IllegalArgumentException("The resource ARN must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.RESOURCE_TAG_KEYS))) {
                List<String> tagKeys = exchange.getIn().getHeader(Lambda2Constants.RESOURCE_TAG_KEYS, List.class);
                request.tagKeys(tagKeys);
            } else {
                throw new IllegalArgumentException("The tag keys must be specified");
            }
            result = lambdaClient.untagResource(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("untagResource command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void publishVersion(LambdaClient lambdaClient, Exchange exchange) {
        PublishVersionResponse result;
        try {
            PublishVersionRequest.Builder request = PublishVersionRequest.builder().functionName(getEndpoint().getFunction());
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.VERSION_DESCRIPTION))) {
                String description = exchange.getIn().getHeader(Lambda2Constants.VERSION_DESCRIPTION, String.class);
                request.description(description);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.VERSION_REVISION_ID))) {
                String revisionId = exchange.getIn().getHeader(Lambda2Constants.VERSION_REVISION_ID, String.class);
                request.revisionId(revisionId);
            }
            result = lambdaClient.publishVersion(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("publishVersion command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listVersions(LambdaClient lambdaClient, Exchange exchange) {
        ListVersionsByFunctionResponse result;
        try {
            ListVersionsByFunctionRequest request = ListVersionsByFunctionRequest.builder().functionName(getEndpoint().getFunction()).build();
            result = lambdaClient.listVersionsByFunction(request);
        } catch (AwsServiceException ase) {
            LOG.trace("publishVersion command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createAlias(LambdaClient lambdaClient, Exchange exchange) {
        CreateAliasResponse result;
        try {
            CreateAliasRequest.Builder request = CreateAliasRequest.builder().functionName(getEndpoint().getFunction());
            String version = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_VERSION, String.class);
            String aliasName = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, String.class);
            if (ObjectHelper.isEmpty(version) || ObjectHelper.isEmpty(aliasName)) {
                throw new IllegalArgumentException("Function Version and alias must be specified to create an alias");
            }
            request.functionVersion(version);
            request.name(aliasName);
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_DESCRIPTION))) {
                String aliasDescription = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_DESCRIPTION, String.class);
                request.description(aliasDescription);
            }
            result = lambdaClient.createAlias(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("createAlias command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteAlias(LambdaClient lambdaClient, Exchange exchange) {
        DeleteAliasResponse result;
        try {
            DeleteAliasRequest.Builder request = DeleteAliasRequest.builder().functionName(getEndpoint().getFunction());
            String aliasName = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, String.class);
            if (ObjectHelper.isEmpty(aliasName)) {
                throw new IllegalArgumentException("Function alias must be specified to delete an alias");
            }
            request.name(aliasName);
            result = lambdaClient.deleteAlias(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("deleteAlias command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void getAlias(LambdaClient lambdaClient, Exchange exchange) {
        GetAliasResponse result;
        try {
            GetAliasRequest.Builder request = GetAliasRequest.builder().functionName(getEndpoint().getFunction());
            String aliasName = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_ALIAS_NAME, String.class);
            if (ObjectHelper.isEmpty(aliasName)) {
                throw new IllegalArgumentException("Function alias must be specified to get an alias");
            }
            request.name(aliasName);
            result = lambdaClient.getAlias(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("getAlias command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listAliases(LambdaClient lambdaClient, Exchange exchange) {
        ListAliasesResponse result;
        try {
            ListAliasesRequest.Builder request = ListAliasesRequest.builder().functionName(getEndpoint().getFunction());
            String version = exchange.getIn().getHeader(Lambda2Constants.FUNCTION_VERSION, String.class);
            if (ObjectHelper.isEmpty(version)) {
                throw new IllegalArgumentException("Function Version must be specified to list aliases for a function");
            }
            request.functionVersion(version);
            result = lambdaClient.listAliases(request.build());
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
            operation = getConfiguration().getOperation() == null ? Lambda2Operations.invokeFunction : getConfiguration().getOperation();
        }
        return operation;
    }

    protected Lambda2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public Lambda2Endpoint getEndpoint() {
        return (Lambda2Endpoint)super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
