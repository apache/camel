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
package org.apache.camel.component.mongodb.verifier;

import java.util.Map;

import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.apache.camel.component.mongodb.conf.ConnectionParamsConfiguration;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.CastUtils.cast;

public class MongoComponentVerifierExtension extends DefaultComponentVerifierExtension {
    private static final Logger LOG = LoggerFactory.getLogger(MongoComponentVerifierExtension.class);

    private static final int CONNECTION_TIMEOUT = 2000;

    public MongoComponentVerifierExtension() {
        super("mongodb");
    }

    @Override
    public Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
                .error(ResultErrorHelper.requiresOption("host", parameters))
                .error(ResultErrorHelper.requiresOption("user", parameters))
                .error(ResultErrorHelper.requiresOption("password", parameters));
        return builder.build();
    }

    @Override
    public Result verifyConnectivity(Map<String, Object> parameters) {
        return ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY)
                .error(parameters, this::verifyCredentials)
                .build();
    }

    private void verifyCredentials(ResultBuilder builder, Map<String, Object> parameters) {
        ConnectionParamsConfiguration mongoConf = new ConnectionParamsConfiguration(cast(parameters));
        MongoClientOptions.Builder optionsBuilder = MongoClientOptions.builder();
        // Avoid retry and long timeout
        optionsBuilder.connectTimeout(CONNECTION_TIMEOUT);
        optionsBuilder.serverSelectionTimeout(CONNECTION_TIMEOUT);
        optionsBuilder.maxWaitTime(CONNECTION_TIMEOUT);
        MongoClientURI connectionURI = new MongoClientURI(mongoConf.getMongoClientURI(), optionsBuilder);

        LOG.info("Testing connection against {}", connectionURI);
        try (MongoClient mongoClient = MongoClients.create(connectionURI.getURI())) {
            // Just ping the server
            mongoClient.getDatabase(connectionURI.getDatabase()).runCommand(Document.parse("{ ping: 1 }"));
            LOG.info("Testing connection successful!");
        } catch (MongoSecurityException e) {
            ResultErrorBuilder errorBuilder = ResultErrorBuilder.withCodeAndDescription(
                    VerificationError.StandardCode.AUTHENTICATION,
                    String.format("Unable to authenticate %s against %s authentication database!", mongoConf.getUser(), mongoConf.getAdminDB()));
            builder.error(errorBuilder.build());
        } catch (MongoTimeoutException e) {
            // When there is any connection exception, the driver tries to reconnect until timeout is reached
            // wrapping the original security/socket exception into a timeout exception
            String description;
            VerificationError.StandardCode code = VerificationError.StandardCode.GENERIC;
            if (e.getMessage().contains("com.mongodb.MongoSecurityException")) {
                code = VerificationError.StandardCode.AUTHENTICATION;
                description = String.format("Unable to authenticate %s against %s authentication database!", mongoConf.getUser(), mongoConf.getAdminDB());
            } else if (e.getMessage().contains("com.mongodb.MongoSocket") && e.getMessage().contains("Exception")) {
                description = String.format("Unable to connect to %s!", mongoConf.getHost());
            } else {
                description = String.format("Generic exception while connecting to %s!", mongoConf.getHost());
            }
            ResultErrorBuilder errorBuilder = ResultErrorBuilder.withCodeAndDescription(
                    code,
                    String.format(description));
            builder.error(errorBuilder.build());
        }
    }
}

