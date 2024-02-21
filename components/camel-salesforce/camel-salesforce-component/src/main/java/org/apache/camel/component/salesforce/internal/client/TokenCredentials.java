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
package org.apache.camel.component.salesforce.internal.client;

import java.util.concurrent.Executor;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import org.apache.camel.component.salesforce.internal.SalesforceSession;

public class TokenCredentials extends CallCredentials {

    private final SalesforceSession session;
    public static final Metadata.Key<String> INSTANCE_URL_KEY = keyOf("instanceUrl");
    public static final Metadata.Key<String> SESSION_TOKEN_KEY = keyOf("accessToken");
    public static final Metadata.Key<String> TENANT_ID_KEY = keyOf("tenantId");

    public TokenCredentials(SalesforceSession session) {
        this.session = session;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier metadataApplier) {
        Metadata headers = new Metadata();
        headers.put(INSTANCE_URL_KEY, session.getInstanceUrl());
        headers.put(TENANT_ID_KEY, session.getOrgId());
        headers.put(SESSION_TOKEN_KEY, session.getAccessToken());
        metadataApplier.apply(headers);
    }

    @Override
    public void thisUsesUnstableApi() {

    }

    private static Metadata.Key<String> keyOf(String name) {
        return Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
    }
}
