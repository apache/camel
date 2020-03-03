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
package org.apache.camel.component.aws.lambda;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.CreateAliasRequest;
import com.amazonaws.services.lambda.model.CreateAliasResult;
import com.amazonaws.services.lambda.model.CreateEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.CreateEventSourceMappingResult;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeadLetterConfig;
import com.amazonaws.services.lambda.model.DeleteAliasRequest;
import com.amazonaws.services.lambda.model.DeleteAliasResult;
import com.amazonaws.services.lambda.model.DeleteEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.DeleteEventSourceMappingResult;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionResult;
import com.amazonaws.services.lambda.model.Environment;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.GetAliasRequest;
import com.amazonaws.services.lambda.model.GetAliasResult;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ListAliasesRequest;
import com.amazonaws.services.lambda.model.ListAliasesResult;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsRequest;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsResult;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.ListTagsRequest;
import com.amazonaws.services.lambda.model.ListTagsResult;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionRequest;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionResult;
import com.amazonaws.services.lambda.model.PublishVersionRequest;
import com.amazonaws.services.lambda.model.PublishVersionResult;
import com.amazonaws.services.lambda.model.TagResourceRequest;
import com.amazonaws.services.lambda.model.TagResourceResult;
import com.amazonaws.services.lambda.model.TracingConfig;
import com.amazonaws.services.lambda.model.UntagResourceRequest;
import com.amazonaws.services.lambda.model.UntagResourceResult;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.lambda.model.VpcConfig;
import com.amazonaws.util.IOUtils;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon Web Service Lambda <a
 * href="https://aws.amazon.com/lambda/">AWS Lambda</a>
 */
public class LambdaProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(LambdaProducer.class);

    public LambdaProducer(final Endpoint endpoint) {
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

    private void getFunction(AWSLambda lambdaClient, Exchange exchange) {
        GetFunctionResult result;
        try {
            result = lambdaClient.getFunction(new GetFunctionRequest().withFunctionName(getEndpoint().getFunction()));
        } catch (AmazonServiceException ase) {
            LOG.trace("getFunction command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteFunction(AWSLambda lambdaClient, Exchange exchange) {
        DeleteFunctionResult result;
        try {
            result = lambdaClient.deleteFunction(new DeleteFunctionRequest().withFunctionName(getEndpoint().getFunction()));
        } catch (AmazonServiceException ase) {
            LOG.trace("deleteFunction command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listFunctions(AWSLambda lambdaClient, Exchange exchange) {
        ListFunctionsResult result;
        try {
            result = lambdaClient.listFunctions();
        } catch (AmazonServiceException ase) {
            LOG.trace("listFunctions command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void invokeFunction(AWSLambda lambdaClient, Exchange exchange) {
        InvokeResult result;
        try {
            InvokeRequest request = new InvokeRequest()
                    .withFunctionName(getEndpoint().getFunction())
                    .withPayload(exchange.getIn().getBody(String.class));
            result = lambdaClient.invoke(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("invokeFunction command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(StandardCharsets.UTF_8.decode(result.getPayload()).toString());
    }

    private void createFunction(AWSLambda lambdaClient, Exchange exchange) throws Exception {
        CreateFunctionResult result;

        try {
            CreateFunctionRequest request = new CreateFunctionRequest()
                    .withFunctionName(getEndpoint().getFunction());

            FunctionCode functionCode = new FunctionCode();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.S3_BUCKET))) {
                String s3Bucket = exchange.getIn().getHeader(LambdaConstants.S3_BUCKET, String.class);
                functionCode.withS3Bucket(s3Bucket);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.S3_KEY))) {
                String s3Key = exchange.getIn().getHeader(LambdaConstants.S3_KEY, String.class);
                functionCode.withS3Key(s3Key);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.S3_OBJECT_VERSION))) {
                String s3ObjectVersion = exchange.getIn().getHeader(LambdaConstants.S3_OBJECT_VERSION, String.class);
                functionCode.withS3ObjectVersion(s3ObjectVersion);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.ZIP_FILE))) {
                String zipFile = exchange.getIn().getHeader(LambdaConstants.ZIP_FILE, String.class);
                File fileLocalPath = new File(zipFile);
                try (FileInputStream inputStream = new FileInputStream(fileLocalPath)) {
                    functionCode.withZipFile(ByteBuffer.wrap(IOUtils.toByteArray(inputStream)));
                }
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getBody())) {
                functionCode.withZipFile(exchange.getIn().getBody(ByteBuffer.class));
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getBody())
                    || (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.S3_BUCKET)) && ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.S3_KEY)))) {
                request.withCode(functionCode);
            } else {
                throw new IllegalArgumentException("At least S3 bucket/S3 key or zip file must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.ROLE))) {
                request.withRole(exchange.getIn().getHeader(LambdaConstants.ROLE, String.class));
            } else {
                throw new IllegalArgumentException("Role must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.RUNTIME))) {
                request.withRuntime(exchange.getIn().getHeader(LambdaConstants.RUNTIME, String.class));
            } else {
                throw new IllegalArgumentException("Runtime must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.HANDLER))) {
                request.withHandler(exchange.getIn().getHeader(LambdaConstants.HANDLER, String.class));
            } else {
                throw new IllegalArgumentException("Handler must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.DESCRIPTION))) {
                String description = exchange.getIn().getHeader(LambdaConstants.DESCRIPTION, String.class);
                request.withDescription(description);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.TARGET_ARN))) {
                String targetArn = exchange.getIn().getHeader(LambdaConstants.TARGET_ARN, String.class);
                request.withDeadLetterConfig(new DeadLetterConfig().withTargetArn(targetArn));
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.MEMORY_SIZE))) {
                Integer memorySize = exchange.getIn().getHeader(LambdaConstants.MEMORY_SIZE, Integer.class);
                request.withMemorySize(memorySize);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.KMS_KEY_ARN))) {
                String kmsKeyARN = exchange.getIn().getHeader(LambdaConstants.KMS_KEY_ARN, String.class);
                request.withKMSKeyArn(kmsKeyARN);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.PUBLISH))) {
                Boolean publish = exchange.getIn().getHeader(LambdaConstants.PUBLISH, Boolean.class);
                request.withPublish(publish);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.TIMEOUT, Integer.class);
                request.withTimeout(timeout);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.TRACING_CONFIG))) {
                String tracingConfigMode = exchange.getIn().getHeader(LambdaConstants.TRACING_CONFIG, String.class);
                request.withTracingConfig(new TracingConfig().withMode(tracingConfigMode));
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT, Integer.class);
                request.withSdkClientExecutionTimeout(timeout);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT, Integer.class);
                request.withSdkRequestTimeout(timeout);
            }

            Map<String, String> environmentVariables = CastUtils.cast(exchange.getIn().getHeader(LambdaConstants.ENVIRONMENT_VARIABLES, Map.class));
            if (environmentVariables != null) {
                request.withEnvironment(new Environment().withVariables(environmentVariables));
            }

            Map<String, String> tags = CastUtils.cast(exchange.getIn().getHeader(LambdaConstants.TAGS, Map.class));
            if (tags != null) {
                request.withTags(tags);
            }

            List<String> securityGroupIds = CastUtils.cast(exchange.getIn().getHeader(LambdaConstants.SECURITY_GROUP_IDS, (Class<List<String>>) (Object) List.class));
            List<String> subnetIds = CastUtils.cast(exchange.getIn().getHeader(LambdaConstants.SUBNET_IDS, (Class<List<String>>) (Object) List.class));
            if (securityGroupIds != null || subnetIds != null) {
                VpcConfig vpcConfig = new VpcConfig();
                if (securityGroupIds != null) {
                    vpcConfig.withSecurityGroupIds(securityGroupIds);
                }
                if (subnetIds != null) {
                    vpcConfig.withSubnetIds(subnetIds);
                }
                request.withVpcConfig(vpcConfig);
            }
            result = lambdaClient.createFunction(request);

        } catch (AmazonServiceException ase) {
            LOG.trace("createFunction command returned the error code {}", ase.getErrorCode());
            throw ase;
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void updateFunction(AWSLambda lambdaClient, Exchange exchange) throws Exception {
        UpdateFunctionCodeResult result;

        try {
            UpdateFunctionCodeRequest request = new UpdateFunctionCodeRequest()
                    .withFunctionName(getEndpoint().getFunction());

            FunctionCode functionCode = new FunctionCode();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.S3_BUCKET))) {
                String s3Bucket = exchange.getIn().getHeader(LambdaConstants.S3_BUCKET, String.class);
                functionCode.withS3Bucket(s3Bucket);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.S3_KEY))) {
                String s3Key = exchange.getIn().getHeader(LambdaConstants.S3_KEY, String.class);
                functionCode.withS3Key(s3Key);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.S3_OBJECT_VERSION))) {
                String s3ObjectVersion = exchange.getIn().getHeader(LambdaConstants.S3_OBJECT_VERSION, String.class);
                functionCode.withS3ObjectVersion(s3ObjectVersion);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.ZIP_FILE))) {
                String zipFile = exchange.getIn().getHeader(LambdaConstants.ZIP_FILE, String.class);
                File fileLocalPath = new File(zipFile);
                try (FileInputStream inputStream = new FileInputStream(fileLocalPath)) {
                    functionCode.withZipFile(ByteBuffer.wrap(IOUtils.toByteArray(inputStream)));
                }
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getBody())) {
                functionCode.withZipFile(exchange.getIn().getBody(ByteBuffer.class));
            }

            if (ObjectHelper.isEmpty(exchange.getIn().getBody())
                    && (ObjectHelper.isEmpty(exchange.getIn().getHeader(LambdaConstants.S3_BUCKET)) && ObjectHelper.isEmpty(exchange.getIn().getHeader(LambdaConstants.S3_KEY)))) {
                throw new IllegalArgumentException("At least S3 bucket/S3 key or zip file must be specified");
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.PUBLISH))) {
                Boolean publish = exchange.getIn().getHeader(LambdaConstants.PUBLISH, Boolean.class);
                request.withPublish(publish);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT, Integer.class);
                request.withSdkClientExecutionTimeout(timeout);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT, Integer.class);
                request.withSdkRequestTimeout(timeout);
            }

            result = lambdaClient.updateFunctionCode(request);

        } catch (AmazonServiceException ase) {
            LOG.trace("updateFunction command returned the error code {}", ase.getErrorCode());
            throw ase;
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createEventSourceMapping(AWSLambda lambdaClient, Exchange exchange) {
        CreateEventSourceMappingResult result;
        try {
            CreateEventSourceMappingRequest request = new CreateEventSourceMappingRequest().withFunctionName(getEndpoint().getFunction());
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.EVENT_SOURCE_ARN))) {
                request.withEventSourceArn(exchange.getIn().getHeader(LambdaConstants.EVENT_SOURCE_ARN, String.class));
            } else {
                throw new IllegalArgumentException("Event Source Arn must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.EVENT_SOURCE_BATCH_SIZE))) {
                Integer batchSize = exchange.getIn().getHeader(LambdaConstants.EVENT_SOURCE_BATCH_SIZE, Integer.class);
                request.withBatchSize(batchSize);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT, Integer.class);
                request.withSdkClientExecutionTimeout(timeout);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT, Integer.class);
                request.withSdkRequestTimeout(timeout);
            }
            result = lambdaClient.createEventSourceMapping(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("createEventSourceMapping command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteEventSourceMapping(AWSLambda lambdaClient, Exchange exchange) {
        DeleteEventSourceMappingResult result;
        try {
            DeleteEventSourceMappingRequest request = new DeleteEventSourceMappingRequest();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.EVENT_SOURCE_UUID))) {
                request.withUUID(exchange.getIn().getHeader(LambdaConstants.EVENT_SOURCE_UUID, String.class));
            } else {
                throw new IllegalArgumentException("Event Source Arn must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT, Integer.class);
                request.withSdkClientExecutionTimeout(timeout);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT, Integer.class);
                request.withSdkRequestTimeout(timeout);
            }
            result = lambdaClient.deleteEventSourceMapping(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("deleteEventSourceMapping command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listEventSourceMapping(AWSLambda lambdaClient, Exchange exchange) {
        ListEventSourceMappingsResult result;
        try {
            ListEventSourceMappingsRequest request = new ListEventSourceMappingsRequest().withFunctionName(getEndpoint().getFunction());
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_CLIENT_EXECUTION_TIMEOUT, Integer.class);
                request.withSdkClientExecutionTimeout(timeout);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT))) {
                Integer timeout = exchange.getIn().getHeader(LambdaConstants.SDK_REQUEST_TIMEOUT, Integer.class);
                request.withSdkRequestTimeout(timeout);
            }
            result = lambdaClient.listEventSourceMappings(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("listEventSourceMapping command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listTags(AWSLambda lambdaClient, Exchange exchange) {
        ListTagsResult result;
        try {
            ListTagsRequest request = new ListTagsRequest();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.RESOURCE_ARN))) {
                String resource = exchange.getIn().getHeader(LambdaConstants.RESOURCE_ARN, String.class);
                request.withResource(resource);
            } else {
                throw new IllegalArgumentException("The resource ARN must be specified");
            }
            result = lambdaClient.listTags(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("listTags command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void tagResource(AWSLambda lambdaClient, Exchange exchange) {
        TagResourceResult result;
        try {
            TagResourceRequest request = new TagResourceRequest();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.RESOURCE_ARN))) {
                String resource = exchange.getIn().getHeader(LambdaConstants.RESOURCE_ARN, String.class);
                request.withResource(resource);
            } else {
                throw new IllegalArgumentException("The resource ARN must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.RESOURCE_TAGS))) {
                Map<String, String> tags = exchange.getIn().getHeader(LambdaConstants.RESOURCE_TAGS, Map.class);
                request.withTags(tags);
            } else {
                throw new IllegalArgumentException("The tags must be specified");
            }
            result = lambdaClient.tagResource(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("listTags command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void untagResource(AWSLambda lambdaClient, Exchange exchange) {
        UntagResourceResult result;
        try {
            UntagResourceRequest request = new UntagResourceRequest();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.RESOURCE_ARN))) {
                String resource = exchange.getIn().getHeader(LambdaConstants.RESOURCE_ARN, String.class);
                request.withResource(resource);
            } else {
                throw new IllegalArgumentException("The resource ARN must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.RESOURCE_TAG_KEYS))) {
                List<String> tagKeys = exchange.getIn().getHeader(LambdaConstants.RESOURCE_TAG_KEYS, List.class);
                request.withTagKeys(tagKeys);
            } else {
                throw new IllegalArgumentException("The tag keys must be specified");
            }
            result = lambdaClient.untagResource(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("untagResource command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void publishVersion(AWSLambda lambdaClient, Exchange exchange) {
        PublishVersionResult result;
        try {
            PublishVersionRequest request = new PublishVersionRequest().withFunctionName(getEndpoint().getFunction());
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.VERSION_DESCRIPTION))) {
                String description = exchange.getIn().getHeader(LambdaConstants.VERSION_DESCRIPTION, String.class);
                request.withDescription(description);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.VERSION_REVISION_ID))) {
                String revisionId = exchange.getIn().getHeader(LambdaConstants.VERSION_REVISION_ID, String.class);
                request.withRevisionId(revisionId);
            }
            result = lambdaClient.publishVersion(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("publishVersion command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listVersions(AWSLambda lambdaClient, Exchange exchange) {
        ListVersionsByFunctionResult result;
        try {
            ListVersionsByFunctionRequest request = new ListVersionsByFunctionRequest().withFunctionName(getEndpoint().getFunction());
            result = lambdaClient.listVersionsByFunction(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("publishVersion command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createAlias(AWSLambda lambdaClient, Exchange exchange) {
        CreateAliasResult result;
        try {
            CreateAliasRequest request = new CreateAliasRequest().withFunctionName(getEndpoint().getFunction());
            String version = exchange.getIn().getHeader(LambdaConstants.FUNCTION_VERSION, String.class);
            String aliasName = exchange.getIn().getHeader(LambdaConstants.FUNCTION_ALIAS_NAME, String.class);
            if (ObjectHelper.isEmpty(version) || ObjectHelper.isEmpty(aliasName)) {
                throw new IllegalArgumentException("Function Version and alias must be specified to create an alias");
            }
            request.setFunctionVersion(version);
            request.setName(aliasName);
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(LambdaConstants.FUNCTION_ALIAS_DESCRIPTION))) {
                String aliasDescription = exchange.getIn().getHeader(LambdaConstants.FUNCTION_ALIAS_DESCRIPTION, String.class);
                request.setDescription(aliasDescription);
            }
            result = lambdaClient.createAlias(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("createAlias command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteAlias(AWSLambda lambdaClient, Exchange exchange) {
        DeleteAliasResult result;
        try {
            DeleteAliasRequest request = new DeleteAliasRequest().withFunctionName(getEndpoint().getFunction());
            String aliasName = exchange.getIn().getHeader(LambdaConstants.FUNCTION_ALIAS_NAME, String.class);
            if (ObjectHelper.isEmpty(aliasName)) {
                throw new IllegalArgumentException("Function alias must be specified to delete an alias");
            }
            request.setName(aliasName);
            result = lambdaClient.deleteAlias(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("deleteAlias command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void getAlias(AWSLambda lambdaClient, Exchange exchange) {
        GetAliasResult result;
        try {
            GetAliasRequest request = new GetAliasRequest().withFunctionName(getEndpoint().getFunction());
            String aliasName = exchange.getIn().getHeader(LambdaConstants.FUNCTION_ALIAS_NAME, String.class);
            if (ObjectHelper.isEmpty(aliasName)) {
                throw new IllegalArgumentException("Function alias must be specified to get an alias");
            }
            request.setName(aliasName);
            result = lambdaClient.getAlias(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("getAlias command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listAliases(AWSLambda lambdaClient, Exchange exchange) {
        ListAliasesResult result;
        try {
            ListAliasesRequest request = new ListAliasesRequest().withFunctionName(getEndpoint().getFunction());
            String version = exchange.getIn().getHeader(LambdaConstants.FUNCTION_VERSION, String.class);
            if (ObjectHelper.isEmpty(version)) {
                throw new IllegalArgumentException("Function Version must be specified to list aliases for a function");
            }
            request.withFunctionVersion(version);
            result = lambdaClient.listAliases(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("listAliases command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private LambdaOperations determineOperation(Exchange exchange) {
        LambdaOperations operation = exchange.getIn().getHeader(LambdaConstants.OPERATION, LambdaOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation() == null ? LambdaOperations.invokeFunction : getConfiguration().getOperation();
        }
        return operation;
    }

    protected LambdaConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public LambdaEndpoint getEndpoint() {
        return (LambdaEndpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
