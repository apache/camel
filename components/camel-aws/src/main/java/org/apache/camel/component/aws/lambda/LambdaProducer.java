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
package org.apache.camel.component.aws.lambda;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeadLetterConfig;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionResult;
import com.amazonaws.services.lambda.model.Environment;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.TracingConfig;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.lambda.model.VpcConfig;
import com.amazonaws.util.IOUtils;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.aws.common.AwsExchangeUtil.getMessageForResponse;

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
        default:
            throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void getFunction(AWSLambda lambdaClient, Exchange exchange) {
        GetFunctionResult result;
        try {
            result = lambdaClient.getFunction(new GetFunctionRequest().withFunctionName(getConfiguration().getFunction()));
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
            result = lambdaClient.deleteFunction(new DeleteFunctionRequest().withFunctionName(getConfiguration().getFunction()));
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
                .withFunctionName(getConfiguration().getFunction())
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
                .withFunctionName(getConfiguration().getFunction());

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
                FileInputStream inputStream = new FileInputStream(fileLocalPath);
                functionCode.withZipFile(ByteBuffer.wrap(IOUtils.toByteArray(inputStream)));
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
                .withFunctionName(getConfiguration().getFunction());

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
                FileInputStream inputStream = new FileInputStream(fileLocalPath);
                functionCode.withZipFile(ByteBuffer.wrap(IOUtils.toByteArray(inputStream)));
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
    
    private LambdaOperations determineOperation(Exchange exchange) {
        LambdaOperations operation = exchange.getIn().getHeader(LambdaConstants.OPERATION, LambdaOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
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

}
