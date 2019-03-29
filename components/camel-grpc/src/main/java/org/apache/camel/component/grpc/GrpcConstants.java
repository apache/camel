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
package org.apache.camel.component.grpc;

import io.grpc.Context;
import io.grpc.Metadata;

/**
 * gRPC component constants
 */
public interface GrpcConstants {

    String GRPC_SERVICE_CLASS_POSTFIX = "Grpc";
    String GRPC_SERVER_IMPL_POSTFIX = "ImplBase";
    String GRPC_SERVICE_SYNC_STUB_METHOD = "newBlockingStub";
    String GRPC_SERVICE_ASYNC_STUB_METHOD = "newStub";
    String GRPC_SERVICE_FUTURE_STUB_METHOD = "newFutureStub";
    String GRPC_SERVICE_STUB_CALL_CREDS_METHOD = "withCallCredentials";
    
    /*
     * JSON Web Tokens specific constants
     */
    String GRPC_JWT_TOKEN_KEY = "jwt";
    String GRPC_USER_ID_KEY = "userId";
    Metadata.Key<String> GRPC_JWT_METADATA_KEY = Metadata.Key.of(GRPC_JWT_TOKEN_KEY, Metadata.ASCII_STRING_MARSHALLER);
    Context.Key<String> GRPC_JWT_CTX_KEY = Context.key(GRPC_JWT_TOKEN_KEY);
    Context.Key<String> GRPC_JWT_USER_ID_CTX_KEY = Context.key(GRPC_USER_ID_KEY);
    
    /*
     * This headers will be set after gRPC consumer method is invoked
     */
    String GRPC_METHOD_NAME_HEADER = "CamelGrpcMethodName";
    String GRPC_USER_AGENT_HEADER = "CamelGrpcUserAgent";
    String GRPC_EVENT_TYPE_HEADER = "CamelGrpcEventType";
    
    String GRPC_EVENT_TYPE_ON_NEXT = "onNext";
    String GRPC_EVENT_TYPE_ON_ERROR = "onError";
    String GRPC_EVENT_TYPE_ON_COMPLETED = "onCompleted";
}
