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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaServiceClientConfiguration;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.AddPermissionResponse;
import software.amazon.awssdk.services.lambda.model.AliasConfiguration;
import software.amazon.awssdk.services.lambda.model.CreateAliasRequest;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.DeleteAliasRequest;
import software.amazon.awssdk.services.lambda.model.DeleteAliasResponse;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.EventSourceMappingConfiguration;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.FunctionUrlAuthType;
import software.amazon.awssdk.services.lambda.model.FunctionUrlConfig;
import software.amazon.awssdk.services.lambda.model.GetAliasRequest;
import software.amazon.awssdk.services.lambda.model.GetAliasResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.GetPolicyRequest;
import software.amazon.awssdk.services.lambda.model.GetPolicyResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.ListAliasesRequest;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsRequest;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionUrlConfigsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionUrlConfigsResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.ListTagsRequest;
import software.amazon.awssdk.services.lambda.model.ListTagsResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionRequest;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.RemovePermissionRequest;
import software.amazon.awssdk.services.lambda.model.RemovePermissionResponse;
import software.amazon.awssdk.services.lambda.model.TagResourceRequest;
import software.amazon.awssdk.services.lambda.model.TagResourceResponse;
import software.amazon.awssdk.services.lambda.model.TracingConfigResponse;
import software.amazon.awssdk.services.lambda.model.TracingMode;
import software.amazon.awssdk.services.lambda.model.UntagResourceRequest;
import software.amazon.awssdk.services.lambda.model.UntagResourceResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionUrlConfigResponse;

public class AmazonLambdaClientMock implements LambdaClient {

    public AmazonLambdaClientMock() {
    }

    @Override
    public CreateAliasResponse createAlias(CreateAliasRequest createAliasRequest) {
        CreateAliasResponse.Builder result = CreateAliasResponse.builder();
        result.functionVersion("1");
        result.name("alias");
        result.description("an alias");
        return result.build();
    }

    @Override
    public CreateEventSourceMappingResponse createEventSourceMapping(
            CreateEventSourceMappingRequest createEventSourceMappingRequest) {
        CreateEventSourceMappingResponse.Builder result = CreateEventSourceMappingResponse.builder();
        result.batchSize(100);
        result.functionArn(
                "arn:aws:lambda:eu-central-1:643534317684:function:" + createEventSourceMappingRequest.functionName());
        result.state("Enabled");
        result.eventSourceArn("arn:aws:sqs:eu-central-1:643534317684:testqueue");
        return result.build();
    }

    @Override
    public CreateFunctionResponse createFunction(CreateFunctionRequest createFunctionRequest) {

        CreateFunctionResponse.Builder result = CreateFunctionResponse.builder();

        result.functionName(createFunctionRequest.functionName());
        result.deadLetterConfig(createFunctionRequest.deadLetterConfig());
        result.description(createFunctionRequest.description());
        result.handler(createFunctionRequest.handler());
        result.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + createFunctionRequest.functionName());

        result.role("arn:aws:iam::643534317684:role/" + createFunctionRequest.role());
        result.codeSize(340L);
        result.codeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO");
        result.memorySize(128);
        result.timeout(3);
        result.lastModified(Instant.now().toString());
        result.version("$LATEST");
        result.tracingConfig(TracingConfigResponse.builder().mode(TracingMode.PASS_THROUGH).build());
        return result.build();
    }

    @Override
    public DeleteAliasResponse deleteAlias(DeleteAliasRequest deleteAliasRequest) {
        return DeleteAliasResponse.builder().build();
    }

    @Override
    public DeleteEventSourceMappingResponse deleteEventSourceMapping(
            DeleteEventSourceMappingRequest deleteEventSourceMappingRequest) {
        return DeleteEventSourceMappingResponse.builder().uuid("a1239494949382882383").state("Deleting").build();
    }

    @Override
    public DeleteFunctionResponse deleteFunction(DeleteFunctionRequest deleteFunctionRequest) {
        return DeleteFunctionResponse.builder().build();
    }

    @Override
    public GetAliasResponse getAlias(GetAliasRequest getAliasRequest) {
        return GetAliasResponse.builder().name("alias").description("an alias").functionVersion("1").build();
    }

    @Override
    public GetFunctionResponse getFunction(GetFunctionRequest getFunctionRequest) {

        GetFunctionResponse.Builder result = GetFunctionResponse.builder();
        FunctionConfiguration.Builder configuration = FunctionConfiguration.builder();
        configuration.functionName(getFunctionRequest.functionName());
        configuration.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + getFunctionRequest.functionName());
        configuration.runtime("nodejs6.10");
        configuration.role("arn:aws:iam::643534317684:role/lambda-execution-role");
        configuration.handler(getFunctionRequest.functionName() + ".handler");
        configuration.codeSize(640L);
        configuration.codeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO/eBH06mBA=");
        configuration.memorySize(128);
        configuration.timeout(3);
        configuration.lastModified(Instant.now().toString());
        configuration.version("$LATEST");
        configuration.tracingConfig(TracingConfigResponse.builder().mode(TracingMode.PASS_THROUGH).build());
        result.configuration(configuration.build());
        return result.build();
    }

    @Override
    public InvokeResponse invoke(InvokeRequest invokeRequest) {
        InvokeResponse.Builder result = InvokeResponse.builder();

        Map<String, String> payload = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            payload = mapper.readValue(invokeRequest.payload().asUtf8String(), new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {

        }
        String responsePayload = "{\"Hello\":\"" + payload.get("name") + "\"}";
        result.payload(SdkBytes.fromByteArray(responsePayload.getBytes()));
        return result.build();
    }

    @Override
    public ListAliasesResponse listAliases(ListAliasesRequest listAliasesRequest) {
        ListAliasesResponse.Builder result = ListAliasesResponse.builder();
        AliasConfiguration.Builder conf = AliasConfiguration.builder();
        List<AliasConfiguration> list = new ArrayList<AliasConfiguration>();
        conf.name("alias");
        conf.description("an alias");
        conf.functionVersion("1");
        list.add(conf.build());
        result.aliases(list);
        return result.build();
    }

    @Override
    public ListEventSourceMappingsResponse listEventSourceMappings(
            ListEventSourceMappingsRequest listEventSourceMappingsRequest) {
        ListEventSourceMappingsResponse.Builder result = ListEventSourceMappingsResponse.builder();
        List<EventSourceMappingConfiguration> confList = new ArrayList<>();
        EventSourceMappingConfiguration.Builder conf = EventSourceMappingConfiguration.builder();
        conf.batchSize(100);
        conf.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + listEventSourceMappingsRequest.functionName());
        conf.state("Enabled");
        conf.eventSourceArn("arn:aws:sqs:eu-central-1:643534317684:testqueue");
        confList.add(conf.build());
        result.eventSourceMappings(confList);
        return result.build();
    }

    @Override
    public ListFunctionsResponse listFunctions() {

        ListFunctionsResponse.Builder result = ListFunctionsResponse.builder();
        Collection<FunctionConfiguration> listFunctions = new ArrayList<>();
        FunctionConfiguration.Builder configuration = FunctionConfiguration.builder();
        configuration.functionName("GetHelloWithName");
        configuration.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
        configuration.runtime("nodejs6.10");
        configuration.role("arn:aws:iam::643534317684:role/lambda-execution-role");
        configuration.handler("GetHelloWithName.handler");
        configuration.codeSize(640L);
        configuration.codeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO/eBH06mBA=");
        configuration.memorySize(128);
        configuration.timeout(3);
        configuration.lastModified(Instant.now().toString());
        configuration.version("$LATEST");
        configuration.tracingConfig(TracingConfigResponse.builder().mode(TracingMode.PASS_THROUGH).build());
        listFunctions.add(configuration.build());
        result.functions(listFunctions);
        return result.build();
    }

    @Override
    public ListFunctionsResponse listFunctions(ListFunctionsRequest request) {

        ListFunctionsResponse.Builder result = ListFunctionsResponse.builder();
        Collection<FunctionConfiguration> listFunctions = new ArrayList<>();
        FunctionConfiguration.Builder configuration = FunctionConfiguration.builder();
        configuration.functionName("GetHelloWithName");
        configuration.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
        configuration.runtime("nodejs6.10");
        configuration.role("arn:aws:iam::643534317684:role/lambda-execution-role");
        configuration.handler("GetHelloWithName.handler");
        configuration.codeSize(640L);
        configuration.codeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO/eBH06mBA=");
        configuration.memorySize(128);
        configuration.timeout(3);
        configuration.lastModified(Instant.now().toString());
        configuration.version("$LATEST");
        configuration.tracingConfig(TracingConfigResponse.builder().mode(TracingMode.PASS_THROUGH).build());
        listFunctions.add(configuration.build());
        result.functions(listFunctions);
        return result.build();
    }

    @Override
    public ListTagsResponse listTags(ListTagsRequest listTagsRequest) {
        ListTagsResponse.Builder result = ListTagsResponse.builder();
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("test", "lambda-tag");
        result.tags(map);
        return result.build();
    }

    @Override
    public ListVersionsByFunctionResponse listVersionsByFunction(ListVersionsByFunctionRequest listVersionsByFunctionRequest) {
        ListVersionsByFunctionResponse.Builder res = ListVersionsByFunctionResponse.builder();
        FunctionConfiguration.Builder conf = FunctionConfiguration.builder();
        conf.version("1");
        conf.functionName(listVersionsByFunctionRequest.functionName());
        res.versions(Collections.singleton(conf.build()));
        return res.build();
    }

    @Override
    public PublishVersionResponse publishVersion(PublishVersionRequest publishVersionRequest) {
        PublishVersionResponse.Builder res = PublishVersionResponse.builder();
        res.functionName(publishVersionRequest.functionName());
        res.description(publishVersionRequest.description());
        return res.build();
    }

    @Override
    public TagResourceResponse tagResource(TagResourceRequest tagResourceRequest) {
        return TagResourceResponse.builder().build();
    }

    @Override
    public UntagResourceResponse untagResource(UntagResourceRequest untagResourceRequest) {
        return UntagResourceResponse.builder().build();
    }

    @Override
    public UpdateFunctionCodeResponse updateFunctionCode(UpdateFunctionCodeRequest updateFunctionCodeRequest) {
        UpdateFunctionCodeResponse.Builder result = UpdateFunctionCodeResponse.builder();

        result.functionName(updateFunctionCodeRequest.functionName());
        result.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + updateFunctionCodeRequest.functionName());
        result.codeSize(340L);
        result.codeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO");
        result.memorySize(128);
        result.timeout(3);
        result.lastModified(Instant.now().toString());
        result.version("$LATEST");
        result.tracingConfig(TracingConfigResponse.builder().mode(TracingMode.PASS_THROUGH).build());
        return result.build();
    }

    // Function URL operations

    @Override
    public CreateFunctionUrlConfigResponse createFunctionUrlConfig(CreateFunctionUrlConfigRequest request) {
        CreateFunctionUrlConfigResponse.Builder result = CreateFunctionUrlConfigResponse.builder();
        result.functionUrl("https://" + request.functionName() + ".lambda-url.eu-central-1.on.aws/");
        result.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + request.functionName());
        result.authType(request.authType());
        result.creationTime(Instant.now().toString());
        if (request.cors() != null) {
            result.cors(request.cors());
        }
        return result.build();
    }

    @Override
    public GetFunctionUrlConfigResponse getFunctionUrlConfig(GetFunctionUrlConfigRequest request) {
        GetFunctionUrlConfigResponse.Builder result = GetFunctionUrlConfigResponse.builder();
        result.functionUrl("https://" + request.functionName() + ".lambda-url.eu-central-1.on.aws/");
        result.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + request.functionName());
        result.authType(FunctionUrlAuthType.NONE);
        result.creationTime(Instant.now().toString());
        result.lastModifiedTime(Instant.now().toString());
        return result.build();
    }

    @Override
    public UpdateFunctionUrlConfigResponse updateFunctionUrlConfig(UpdateFunctionUrlConfigRequest request) {
        UpdateFunctionUrlConfigResponse.Builder result = UpdateFunctionUrlConfigResponse.builder();
        result.functionUrl("https://" + request.functionName() + ".lambda-url.eu-central-1.on.aws/");
        result.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + request.functionName());
        result.authType(request.authType() != null ? request.authType() : FunctionUrlAuthType.NONE);
        result.creationTime(Instant.now().toString());
        result.lastModifiedTime(Instant.now().toString());
        if (request.cors() != null) {
            result.cors(request.cors());
        }
        return result.build();
    }

    @Override
    public DeleteFunctionUrlConfigResponse deleteFunctionUrlConfig(DeleteFunctionUrlConfigRequest request) {
        return DeleteFunctionUrlConfigResponse.builder().build();
    }

    @Override
    public ListFunctionUrlConfigsResponse listFunctionUrlConfigs(ListFunctionUrlConfigsRequest request) {
        ListFunctionUrlConfigsResponse.Builder result = ListFunctionUrlConfigsResponse.builder();
        FunctionUrlConfig.Builder urlConfig = FunctionUrlConfig.builder();
        urlConfig.functionUrl("https://" + request.functionName() + ".lambda-url.eu-central-1.on.aws/");
        urlConfig.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + request.functionName());
        urlConfig.authType(FunctionUrlAuthType.NONE);
        urlConfig.creationTime(Instant.now().toString());
        urlConfig.lastModifiedTime(Instant.now().toString());
        result.functionUrlConfigs(Collections.singletonList(urlConfig.build()));
        return result.build();
    }

    // Function configuration operations

    @Override
    public GetFunctionConfigurationResponse getFunctionConfiguration(GetFunctionConfigurationRequest request) {
        GetFunctionConfigurationResponse.Builder result = GetFunctionConfigurationResponse.builder();
        result.functionName(request.functionName());
        result.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + request.functionName());
        result.runtime("nodejs6.10");
        result.role("arn:aws:iam::643534317684:role/lambda-execution-role");
        result.handler(request.functionName() + ".handler");
        result.codeSize(640L);
        result.codeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO/eBH06mBA=");
        result.memorySize(128);
        result.timeout(3);
        result.lastModified(Instant.now().toString());
        result.version("$LATEST");
        result.tracingConfig(TracingConfigResponse.builder().mode(TracingMode.PASS_THROUGH).build());
        return result.build();
    }

    @Override
    public UpdateFunctionConfigurationResponse updateFunctionConfiguration(UpdateFunctionConfigurationRequest request) {
        UpdateFunctionConfigurationResponse.Builder result = UpdateFunctionConfigurationResponse.builder();
        result.functionName(request.functionName());
        result.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + request.functionName());
        result.runtime(request.runtime() != null ? request.runtime().toString() : "nodejs6.10");
        result.role(request.role() != null ? request.role() : "arn:aws:iam::643534317684:role/lambda-execution-role");
        result.handler(request.handler() != null ? request.handler() : request.functionName() + ".handler");
        result.codeSize(640L);
        result.codeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO/eBH06mBA=");
        result.memorySize(request.memorySize() != null ? request.memorySize() : 128);
        result.timeout(request.timeout() != null ? request.timeout() : 3);
        result.lastModified(Instant.now().toString());
        result.version("$LATEST");
        result.tracingConfig(TracingConfigResponse.builder().mode(TracingMode.PASS_THROUGH).build());
        return result.build();
    }

    // Concurrency operations

    @Override
    public PutFunctionConcurrencyResponse putFunctionConcurrency(PutFunctionConcurrencyRequest request) {
        PutFunctionConcurrencyResponse.Builder result = PutFunctionConcurrencyResponse.builder();
        result.reservedConcurrentExecutions(request.reservedConcurrentExecutions());
        return result.build();
    }

    @Override
    public DeleteFunctionConcurrencyResponse deleteFunctionConcurrency(DeleteFunctionConcurrencyRequest request) {
        return DeleteFunctionConcurrencyResponse.builder().build();
    }

    @Override
    public GetFunctionConcurrencyResponse getFunctionConcurrency(GetFunctionConcurrencyRequest request) {
        GetFunctionConcurrencyResponse.Builder result = GetFunctionConcurrencyResponse.builder();
        result.reservedConcurrentExecutions(100);
        return result.build();
    }

    // Permission operations

    @Override
    public AddPermissionResponse addPermission(AddPermissionRequest request) {
        AddPermissionResponse.Builder result = AddPermissionResponse.builder();
        String statement = "{\"Sid\":\"" + request.statementId() + "\",\"Effect\":\"Allow\",\"Principal\":{\"Service\":\""
                           + request.principal() + "\"},\"Action\":\"" + request.action()
                           + "\",\"Resource\":\"arn:aws:lambda:eu-central-1:643534317684:function:" + request.functionName()
                           + "\"}";
        result.statement(statement);
        return result.build();
    }

    @Override
    public RemovePermissionResponse removePermission(RemovePermissionRequest request) {
        return RemovePermissionResponse.builder().build();
    }

    @Override
    public GetPolicyResponse getPolicy(GetPolicyRequest request) {
        GetPolicyResponse.Builder result = GetPolicyResponse.builder();
        String policy
                = "{\"Version\":\"2012-10-17\",\"Id\":\"default\",\"Statement\":[{\"Sid\":\"test-statement\",\"Effect\":\"Allow\","
                  + "\"Principal\":{\"Service\":\"s3.amazonaws.com\"},\"Action\":\"lambda:InvokeFunction\","
                  + "\"Resource\":\"arn:aws:lambda:eu-central-1:643534317684:function:" + request.functionName() + "\"}]}";
        result.policy(policy);
        result.revisionId("revision-123");
        return result.build();
    }

    @Override
    public LambdaServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public String serviceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }
}
