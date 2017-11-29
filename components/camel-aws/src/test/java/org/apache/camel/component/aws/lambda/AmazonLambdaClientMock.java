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

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.AddPermissionRequest;
import com.amazonaws.services.lambda.model.AddPermissionResult;
import com.amazonaws.services.lambda.model.CreateAliasRequest;
import com.amazonaws.services.lambda.model.CreateAliasResult;
import com.amazonaws.services.lambda.model.CreateEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.CreateEventSourceMappingResult;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeleteAliasRequest;
import com.amazonaws.services.lambda.model.DeleteAliasResult;
import com.amazonaws.services.lambda.model.DeleteEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.DeleteEventSourceMappingResult;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionResult;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetAccountSettingsRequest;
import com.amazonaws.services.lambda.model.GetAccountSettingsResult;
import com.amazonaws.services.lambda.model.GetAliasRequest;
import com.amazonaws.services.lambda.model.GetAliasResult;
import com.amazonaws.services.lambda.model.GetEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.GetEventSourceMappingResult;
import com.amazonaws.services.lambda.model.GetFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.GetFunctionConfigurationResult;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.GetPolicyRequest;
import com.amazonaws.services.lambda.model.GetPolicyResult;
import com.amazonaws.services.lambda.model.InvokeAsyncRequest;
import com.amazonaws.services.lambda.model.InvokeAsyncResult;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ListAliasesRequest;
import com.amazonaws.services.lambda.model.ListAliasesResult;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsRequest;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsResult;
import com.amazonaws.services.lambda.model.ListFunctionsRequest;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.ListTagsRequest;
import com.amazonaws.services.lambda.model.ListTagsResult;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionRequest;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionResult;
import com.amazonaws.services.lambda.model.PublishVersionRequest;
import com.amazonaws.services.lambda.model.PublishVersionResult;
import com.amazonaws.services.lambda.model.RemovePermissionRequest;
import com.amazonaws.services.lambda.model.RemovePermissionResult;
import com.amazonaws.services.lambda.model.Runtime;
import com.amazonaws.services.lambda.model.TagResourceRequest;
import com.amazonaws.services.lambda.model.TagResourceResult;
import com.amazonaws.services.lambda.model.TracingConfigResponse;
import com.amazonaws.services.lambda.model.TracingMode;
import com.amazonaws.services.lambda.model.UntagResourceRequest;
import com.amazonaws.services.lambda.model.UntagResourceResult;
import com.amazonaws.services.lambda.model.UpdateAliasRequest;
import com.amazonaws.services.lambda.model.UpdateAliasResult;
import com.amazonaws.services.lambda.model.UpdateEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.UpdateEventSourceMappingResult;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;

public class AmazonLambdaClientMock extends AWSLambdaClient {

    public AmazonLambdaClientMock() {
        super(new BasicAWSCredentials("user", "secret"));
    }


    @Override
    public AddPermissionResult addPermission(AddPermissionRequest addPermissionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateAliasResult createAlias(CreateAliasRequest createAliasRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateEventSourceMappingResult createEventSourceMapping(CreateEventSourceMappingRequest createEventSourceMappingRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateFunctionResult createFunction(CreateFunctionRequest createFunctionRequest) {

        CreateFunctionResult result = new CreateFunctionResult();

        result.setFunctionName(createFunctionRequest.getFunctionName());
        result.setDeadLetterConfig(createFunctionRequest.getDeadLetterConfig());
        result.setDescription(createFunctionRequest.getDescription());
        result.setHandler(createFunctionRequest.getHandler());
        result.setFunctionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + createFunctionRequest.getFunctionName());

        try {
            Runtime runtime = Runtime.fromValue(createFunctionRequest.getRuntime());
            result.setRuntime(runtime);
        } catch (Exception e) {
            throw new AmazonServiceException("validation error detected: Value '"
                + createFunctionRequest.getRuntime()
                + "' at 'runtime' failed to satisfy constraint: Member must satisfy enum value set: [java8, nodejs, nodejs4.3, nodejs6.10, python2.7, python3.6, dotnetcore1.0]");
        }

        result.setRole("arn:aws:iam::643534317684:role/" + createFunctionRequest.getRole());
        result.setCodeSize(340L);
        result.setCodeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO");
        result.setMemorySize(128);
        result.setTimeout(3);
        result.setLastModified(DateTime.now().toString());
        result.setVersion("$LATEST");
        result.setTracingConfig(new TracingConfigResponse().withMode(TracingMode.PassThrough));
        return result;
    }

    @Override
    public DeleteAliasResult deleteAlias(DeleteAliasRequest deleteAliasRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteEventSourceMappingResult deleteEventSourceMapping(DeleteEventSourceMappingRequest deleteEventSourceMappingRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteFunctionResult deleteFunction(DeleteFunctionRequest deleteFunctionRequest) {
        return new DeleteFunctionResult();
    }

    @Override
    public GetAccountSettingsResult getAccountSettings(GetAccountSettingsRequest getAccountSettingsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetAliasResult getAlias(GetAliasRequest getAliasRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetEventSourceMappingResult getEventSourceMapping(GetEventSourceMappingRequest getEventSourceMappingRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetFunctionResult getFunction(GetFunctionRequest getFunctionRequest) {

        GetFunctionResult result = new GetFunctionResult();
        FunctionConfiguration configuration = new FunctionConfiguration();
        configuration.setFunctionName(getFunctionRequest.getFunctionName());
        configuration.setFunctionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + getFunctionRequest.getFunctionName());
        configuration.setRuntime("nodejs6.10");
        configuration.setRole("arn:aws:iam::643534317684:role/lambda-execution-role");
        configuration.setHandler(getFunctionRequest.getFunctionName() + ".handler");
        configuration.setCodeSize(640L);
        configuration.setCodeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO/eBH06mBA=");
        configuration.setMemorySize(128);
        configuration.setTimeout(3);
        configuration.setLastModified(DateTime.now().toString());
        configuration.setVersion("$LATEST");
        configuration.setTracingConfig(new TracingConfigResponse().withMode(TracingMode.PassThrough));
        result.setConfiguration(configuration);
        return result;
    }

    @Override
    public GetFunctionConfigurationResult getFunctionConfiguration(GetFunctionConfigurationRequest getFunctionConfigurationRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetPolicyResult getPolicy(GetPolicyRequest getPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvokeResult invoke(InvokeRequest invokeRequest) {
        InvokeResult result = new InvokeResult();

        Map<String, Object> payload = new HashMap<String, Object>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            payload = mapper.readValue(StandardCharsets.UTF_8.decode(invokeRequest.getPayload()).toString(), new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {

        }
        String responsePayload = "{\"Hello\":\"" + payload.get("name") + "\"}";
        result.setPayload(ByteBuffer.wrap(responsePayload.getBytes()));
        return result;
    }

    @Override
    public InvokeAsyncResult invokeAsync(InvokeAsyncRequest invokeAsyncRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListAliasesResult listAliases(ListAliasesRequest listAliasesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListEventSourceMappingsResult listEventSourceMappings(ListEventSourceMappingsRequest listEventSourceMappingsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListEventSourceMappingsResult listEventSourceMappings() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListFunctionsResult listFunctions(ListFunctionsRequest listFunctionsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListFunctionsResult listFunctions() {

        ListFunctionsResult result = new ListFunctionsResult();
        Collection<FunctionConfiguration> listFunctions = new ArrayList<FunctionConfiguration>();
        FunctionConfiguration configuration = new FunctionConfiguration();
        configuration.setFunctionName("GetHelloWithName");
        configuration.setFunctionArn("arn:aws:lambda:eu-central-1:643534317684:function:GetHelloWithName");
        configuration.setRuntime("nodejs6.10");
        configuration.setRole("arn:aws:iam::643534317684:role/lambda-execution-role");
        configuration.setHandler("GetHelloWithName.handler");
        configuration.setCodeSize(640L);
        configuration.setCodeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO/eBH06mBA=");
        configuration.setMemorySize(128);
        configuration.setTimeout(3);
        configuration.setLastModified(DateTime.now().toString());
        configuration.setVersion("$LATEST");
        configuration.setTracingConfig(new TracingConfigResponse().withMode(TracingMode.PassThrough));
        listFunctions.add(configuration);
        result.setFunctions(listFunctions);
        return result;
    }

    @Override
    public ListTagsResult listTags(ListTagsRequest listTagsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListVersionsByFunctionResult listVersionsByFunction(ListVersionsByFunctionRequest listVersionsByFunctionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublishVersionResult publishVersion(PublishVersionRequest publishVersionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RemovePermissionResult removePermission(RemovePermissionRequest removePermissionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TagResourceResult tagResource(TagResourceRequest tagResourceRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UntagResourceResult untagResource(UntagResourceRequest untagResourceRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateAliasResult updateAlias(UpdateAliasRequest updateAliasRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateEventSourceMappingResult updateEventSourceMapping(UpdateEventSourceMappingRequest updateEventSourceMappingRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateFunctionCodeResult updateFunctionCode(UpdateFunctionCodeRequest updateFunctionCodeRequest) {
        UpdateFunctionCodeResult result = new UpdateFunctionCodeResult();

        result.setFunctionName(updateFunctionCodeRequest.getFunctionName());
        result.setFunctionArn("arn:aws:lambda:eu-central-1:643534317684:function:" + updateFunctionCodeRequest.getFunctionName());
        result.setCodeSize(340L);
        result.setCodeSha256("PKt5ygvZ6G8vWJASlWIypsBmKzAdmRrvTO");
        result.setMemorySize(128);
        result.setTimeout(3);
        result.setLastModified(DateTime.now().toString());
        result.setVersion("$LATEST");
        result.setTracingConfig(new TracingConfigResponse().withMode(TracingMode.PassThrough));
        return result;
    }

    @Override
    public UpdateFunctionConfigurationResult updateFunctionConfiguration(UpdateFunctionConfigurationRequest updateFunctionConfigurationRequest) {
        throw new UnsupportedOperationException();
    }


    @Override
    public ResponseMetadata getCachedResponseMetadata(AmazonWebServiceRequest amazonWebServiceRequest) {
        throw new UnsupportedOperationException();
    }
}