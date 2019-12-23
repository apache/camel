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
package org.apache.camel.component.dropbox;

import java.util.Locale;
import java.util.Map;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;

public class DropboxComponentVerifierExtension extends DefaultComponentVerifierExtension {

    public DropboxComponentVerifierExtension() {
        this("dropbox");
    }

    protected DropboxComponentVerifierExtension(String scheme) {
        super(scheme);
    }

    // *********************************
    // Parameters validation
    //
    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
                .error(ResultErrorHelper.requiresOption("accessToken", parameters))
                .error(ResultErrorHelper.requiresOption("clientIdentifier", parameters));

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************
    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        return ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY)
                .error(parameters, this::verifyCredentials).build();
    }

    private void verifyCredentials(ResultBuilder builder, Map<String, Object> parameters) {

        String token = (String) parameters.get("accessToken");
        String clientId = (String) parameters.get("clientIdentifier");

        try {
            // Create Dropbox client
            DbxRequestConfig config = new DbxRequestConfig(clientId, Locale.getDefault().toString());
            DbxClientV2 client = new DbxClientV2(config, token);
            client.users().getCurrentAccount();
            client = null;
        } catch (Exception e) {
            builder.error(ResultErrorBuilder
                    .withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION,
                            "Invalid client identifier and/or access token")
                    .parameterKey("accessToken").parameterKey("clientIdentifier").build());
        }

    }

}
