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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.quarkus.websockets.next.HttpUpgradeCheck.CheckResult;
import io.quarkus.websockets.next.HttpUpgradeCheck.HttpUpgradeContext;
import io.quarkus.websockets.next.UserData.TypedKey;
import io.smallrye.mutiny.Uni;

/** Authenticates and reserves capacity before a remote integration can upgrade its HTTP connection. */
@ApplicationScoped
public class RemoteRuntimeUpgradeCheck implements HttpUpgradeCheck {

    static final String ENDPOINT_ID = "camel-cli-connector";
    static final TypedKey<String> RESERVATION_KEY = TypedKey.forString("camel-cli-connector-reservation");

    @Inject
    RemoteRuntimeConfig config;

    @Inject
    RemoteRuntimeRegistry registry;

    @Override
    public boolean appliesTo(String endpointId) {
        return ENDPOINT_ID.equals(endpointId);
    }

    @Override
    public Uni<CheckResult> perform(HttpUpgradeContext context) {
        if (!config.isEnabled()) {
            return CheckResult.rejectUpgrade(404);
        }
        if (!config.isTokenConfigured()) {
            return CheckResult.rejectUpgrade(503);
        }
        String authorization = context.httpRequest().getHeader("Authorization");
        if (!config.isAuthorized(authorization)) {
            return CheckResult.rejectUpgrade(401);
        }
        String executionId;
        try {
            executionId = CliConnectorProtocol.executionId(
                    CliConnectorProtocol.queryFromUri(context.httpRequest().uri()));
        } catch (IllegalArgumentException e) {
            return CheckResult.rejectUpgrade(400);
        }
        String reservation = registry.reserve(config.ownerId(), executionId);
        if (reservation == null) {
            return CheckResult.rejectUpgrade(503);
        }
        context.userData().put(RESERVATION_KEY, reservation);
        return CheckResult.permitUpgrade();
    }
}
