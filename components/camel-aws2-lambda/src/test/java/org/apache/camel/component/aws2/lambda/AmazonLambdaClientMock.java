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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AliasConfiguration;
import software.amazon.awssdk.services.lambda.model.CreateAliasRequest;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteAliasRequest;
import software.amazon.awssdk.services.lambda.model.DeleteAliasResponse;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.EventSourceMappingConfiguration;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
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
import software.amazon.awssdk.services.lambda.model.TracingConfigResponse;
import software.amazon.awssdk.services.lambda.model.TracingMode;
import software.amazon.awssdk.services.lambda.model.UntagResourceRequest;
import software.amazon.awssdk.services.lambda.model.UntagResourceResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;

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
    public CreateEventSourceMappingResponse createEventSourceMapping(CreateEventSourceMappingRequest createEventSourceMappingRequest) {
        CreateEventSourceMappingResponse.Builder result = CreateEventSourceMappingResponse.builder();
        result.batchSize(100);
        result.functionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + createEventSourceMappingRequest.functionName());
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
    public DeleteEventSourceMappingResponse deleteEventSourceMapping(DeleteEventSourceMappingRequest deleteEventSourceMappingRequest) {
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
    public ListEventSourceMappingsResponse listEventSourceMappings(ListEventSourceMappingsRequest listEventSourceMappingsRequest) {
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
