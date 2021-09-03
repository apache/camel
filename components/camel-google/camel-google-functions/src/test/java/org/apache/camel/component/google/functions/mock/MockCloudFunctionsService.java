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
package org.apache.camel.component.google.functions.mock;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.google.api.gax.grpc.testing.MockGrpcService;
import com.google.cloud.functions.v1.CallFunctionRequest;
import com.google.cloud.functions.v1.CallFunctionResponse;
import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CloudFunctionsServiceGrpc.CloudFunctionsServiceImplBase;
import com.google.cloud.functions.v1.CreateFunctionRequest;
import com.google.cloud.functions.v1.DeleteFunctionRequest;
import com.google.cloud.functions.v1.GenerateDownloadUrlRequest;
import com.google.cloud.functions.v1.GenerateDownloadUrlResponse;
import com.google.cloud.functions.v1.GenerateUploadUrlRequest;
import com.google.cloud.functions.v1.GenerateUploadUrlResponse;
import com.google.cloud.functions.v1.GetFunctionRequest;
import com.google.cloud.functions.v1.ListFunctionsRequest;
import com.google.cloud.functions.v1.ListFunctionsResponse;
import com.google.cloud.functions.v1.UpdateFunctionRequest;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.longrunning.Operation;
import com.google.protobuf.AbstractMessage;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;

public class MockCloudFunctionsService extends CloudFunctionsServiceImplBase implements MockGrpcService {
    private List<AbstractMessage> requests;
    private Queue<Object> responses;

    public MockCloudFunctionsService() {
        requests = new ArrayList<>();
        responses = new LinkedList<>();
    }

    public List<AbstractMessage> getRequests() {
        return requests;
    }

    public void addResponse(AbstractMessage response) {
        responses.add(response);
    }

    public void setResponses(List<AbstractMessage> responses) {
        this.responses = new LinkedList<Object>(responses);
    }

    public void addException(Exception exception) {
        responses.add(exception);
    }

    public void reset() {
        requests = new ArrayList<>();
        responses = new LinkedList<>();
    }

    @Override
    public void listFunctions(ListFunctionsRequest request, StreamObserver<ListFunctionsResponse> responseObserver) {
        Object response = responses.remove();
        if (response instanceof ListFunctionsResponse) {
            requests.add(request);
            responseObserver.onNext((ListFunctionsResponse) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method ListFunctions, expected %s or %s",
                            response.getClass().getName(), ListFunctionsResponse.class.getName(), Exception.class.getName())));
        }
    }

    @Override
    public void getFunction(GetFunctionRequest request, StreamObserver<CloudFunction> responseObserver) {
        Object response = responses.remove();
        if (response instanceof CloudFunction) {
            requests.add(request);
            responseObserver.onNext((CloudFunction) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method GetFunction, expected %s or %s",
                            response.getClass().getName(), CloudFunction.class.getName(), Exception.class.getName())));
        }
    }

    @Override
    public void createFunction(CreateFunctionRequest request, StreamObserver<Operation> responseObserver) {
        Object response = responses.remove();
        if (response instanceof Operation) {
            requests.add(request);
            responseObserver.onNext((Operation) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method CreateFunction, expected %s or %s",
                            response.getClass().getName(), Operation.class.getName(), Exception.class.getName())));
        }
    }

    @Override
    public void updateFunction(UpdateFunctionRequest request, StreamObserver<Operation> responseObserver) {
        Object response = responses.remove();
        if (response instanceof Operation) {
            requests.add(request);
            responseObserver.onNext((Operation) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method UpdateFunction, expected %s or %s",
                            response.getClass().getName(), Operation.class.getName(), Exception.class.getName())));
        }
    }

    @Override
    public void deleteFunction(DeleteFunctionRequest request, StreamObserver<Operation> responseObserver) {
        Object response = responses.remove();
        if (response instanceof Operation) {
            requests.add(request);
            responseObserver.onNext((Operation) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method DeleteFunction, expected %s or %s",
                            response.getClass().getName(), Operation.class.getName(), Exception.class.getName())));
        }
    }

    @Override
    public void callFunction(CallFunctionRequest request, StreamObserver<CallFunctionResponse> responseObserver) {
        Object response = responses.remove();
        if (response instanceof CallFunctionResponse) {
            requests.add(request);
            responseObserver.onNext((CallFunctionResponse) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method CallFunction, expected %s or %s",
                            response.getClass().getName(), CallFunctionResponse.class.getName(), Exception.class.getName())));
        }
    }

    @Override
    public void generateUploadUrl(
            GenerateUploadUrlRequest request,
            StreamObserver<GenerateUploadUrlResponse> responseObserver) {
        Object response = responses.remove();
        if (response instanceof GenerateUploadUrlResponse) {
            requests.add(request);
            responseObserver.onNext((GenerateUploadUrlResponse) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method GenerateUploadUrl, expected %s or %s",
                            response.getClass().getName(), GenerateUploadUrlResponse.class.getName(),
                            Exception.class.getName())));
        }
    }

    @Override
    public void generateDownloadUrl(
            GenerateDownloadUrlRequest request,
            StreamObserver<GenerateDownloadUrlResponse> responseObserver) {
        Object response = responses.remove();
        if (response instanceof GenerateDownloadUrlResponse) {
            requests.add(request);
            responseObserver.onNext((GenerateDownloadUrlResponse) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method GenerateDownloadUrl, expected %s or %s",
                            response.getClass().getName(), GenerateDownloadUrlResponse.class.getName(),
                            Exception.class.getName())));
        }
    }

    @Override
    public void setIamPolicy(SetIamPolicyRequest request, StreamObserver<Policy> responseObserver) {
        Object response = responses.remove();
        if (response instanceof Policy) {
            requests.add(request);
            responseObserver.onNext((Policy) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method SetIamPolicy, expected %s or %s",
                            response.getClass().getName(), Policy.class.getName(), Exception.class.getName())));
        }
    }

    @Override
    public void getIamPolicy(GetIamPolicyRequest request, StreamObserver<Policy> responseObserver) {
        Object response = responses.remove();
        if (response instanceof Policy) {
            requests.add(request);
            responseObserver.onNext((Policy) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method GetIamPolicy, expected %s or %s",
                            response.getClass().getName(), Policy.class.getName(), Exception.class.getName())));
        }
    }

    @Override
    public void testIamPermissions(
            TestIamPermissionsRequest request,
            StreamObserver<TestIamPermissionsResponse> responseObserver) {
        Object response = responses.remove();
        if (response instanceof TestIamPermissionsResponse) {
            requests.add(request);
            responseObserver.onNext((TestIamPermissionsResponse) response);
            responseObserver.onCompleted();
        } else if (response instanceof Exception) {
            responseObserver.onError((Exception) response);
        } else {
            responseObserver.onError(new IllegalArgumentException(
                    String.format("Unrecognized response type %s for method TestIamPermissions, expected %s or %s",
                            response.getClass().getName(), TestIamPermissionsResponse.class.getName(),
                            Exception.class.getName())));
        }
    }

    @Override
    public ServerServiceDefinition getServiceDefinition() {
        return bindService();
    }
}
