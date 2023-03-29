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
package org.apache.camel.component.aws2.sts;

import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsServiceClientConfiguration;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.AssumedRoleUser;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetFederationTokenRequest;
import software.amazon.awssdk.services.sts.model.GetFederationTokenResponse;
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest;
import software.amazon.awssdk.services.sts.model.GetSessionTokenResponse;

public class AmazonSTSClientMock implements StsClient {

    public AmazonSTSClientMock() {
    }

    @Override
    public AssumeRoleResponse assumeRole(AssumeRoleRequest assumeRoleRequest) {
        return AssumeRoleResponse.builder().assumedRoleUser(AssumedRoleUser.builder().arn("arn").build()).build();
    }

    @Override
    public GetSessionTokenResponse getSessionToken(GetSessionTokenRequest getSessionTokenRequest) {
        return GetSessionTokenResponse.builder()
                .credentials(Credentials.builder().accessKeyId("xxx").secretAccessKey("yyy").sessionToken("test").build())
                .build();
    }

    @Override
    public StsServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public GetFederationTokenResponse getFederationToken(GetFederationTokenRequest getFederationTokenRequest) {
        return GetFederationTokenResponse.builder()
                .credentials(Credentials.builder().accessKeyId("xxx").secretAccessKey("yyy").sessionToken("test").build())
                .build();
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
