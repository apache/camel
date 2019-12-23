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
package org.apache.camel.component.grpc.auth.jwt;

import java.util.concurrent.Executor;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import org.apache.camel.component.grpc.GrpcConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.grpc.internal.GrpcAttributes.ATTR_LB_ADDR_AUTHORITY;

/**
 * JSON Web Token client credentials generator and injector
 */
public class JwtCallCredentials extends CallCredentials {
    private static final Logger LOG = LoggerFactory.getLogger(JwtCallCredentials.class);
    private final String jwtToken;

    public JwtCallCredentials(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier metadataApplier) {
        String authority = requestInfo.getTransportAttrs().get(ATTR_LB_ADDR_AUTHORITY);

        LOG.debug("Using authority {} for credentials", authority);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    LOG.debug("Start to apply for the JWT token header");
                    Metadata headers = new Metadata();
                    Metadata.Key<String> jwtKey = GrpcConstants.GRPC_JWT_METADATA_KEY;
                    headers.put(jwtKey, jwtToken);
                    metadataApplier.apply(headers);
                } catch (Throwable e) {
                    LOG.debug("Unable to set metadata credentials header", e);
                    metadataApplier.fail(Status.UNAUTHENTICATED.withCause(e));
                }
            }
        });
    }

    @Override
    public void thisUsesUnstableApi() {
    }
}
