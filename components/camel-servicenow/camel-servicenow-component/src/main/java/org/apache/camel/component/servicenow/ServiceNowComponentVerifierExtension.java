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
package org.apache.camel.component.servicenow;

import java.util.Map;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.NoSuchOptionException;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;

final class ServiceNowComponentVerifierExtension extends DefaultComponentVerifierExtension {

    ServiceNowComponentVerifierExtension() {
        super("servicenow");
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS);

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        // Default is success
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY);

        try {
            // Load ServiceNow Configuration
            ServiceNowConfiguration configuration = new ServiceNowConfiguration();
            setProperties(configuration, parameters);

            String instanceName = getMandatoryOption(parameters, "instanceName", String.class);
            String tableName = configuration.getTable() != null ? configuration.getTable() : "incident";

            // Configure Api and OAuthToken ULRs using instanceName
            if (!configuration.hasApiUrl()) {
                configuration.setApiUrl(String.format("https://%s.service-now.com/api", instanceName));
            }
            if (!configuration.hasOauthTokenUrl()) {
                configuration.setOauthTokenUrl(String.format("https://%s.service-now.com/oauth_token.do", instanceName));
            }

            new ServiceNowClient(getCamelContext(), configuration)
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("now")
                .path(configuration.getApiVersion())
                .path("table")
                .path(tableName)
                .query(ServiceNowParams.SYSPARM_LIMIT.getId(), 1L)
                .invoke(HttpMethod.GET);
        } catch (NoSuchOptionException e) {
            builder.error(
                ResultErrorBuilder.withMissingOption(e.getOptionName()).build()
            );
        } catch (ServiceNowException e) {
            ResultErrorBuilder errorBuilder = ResultErrorBuilder.withException(e)
                .detail(VerificationError.HttpAttribute.HTTP_CODE, e.getCode())
                .detail("servicenow_error_message", e.getMessage())
                .detail("servicenow_error_status", e.getStatus())
                .detail("servicenow_error_detail", e.getDetail());

            if (e.getCode() == 401) {
                errorBuilder.code(VerificationError.StandardCode.AUTHENTICATION);
                errorBuilder.parameterKey("userName");
                errorBuilder.parameterKey("password");
                errorBuilder.parameterKey("oauthClientId");
                errorBuilder.parameterKey("oauthClientSecret");
            }

            builder.error(errorBuilder.build());
        } catch (Exception e) {
            builder.error(
                ResultErrorBuilder.withException(e).build()
            );
        }

        return builder.build();
    }
}
