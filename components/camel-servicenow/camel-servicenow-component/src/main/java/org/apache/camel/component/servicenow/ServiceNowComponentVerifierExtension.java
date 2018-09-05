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
import org.apache.camel.util.ObjectHelper;

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
            final ServiceNowConfiguration configuration = getServiceNowConfiguration(parameters);
            final ServiceNowClient client = getServiceNowClient(configuration, parameters);
            final String tableName = ObjectHelper.supplyIfEmpty(configuration.getTable(), () -> "incident");

            client.reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("now")
                .path(configuration.getApiVersion())
                .path("table")
                .path(tableName)
                .query(ServiceNowParams.SYSPARM_LIMIT.getId(), 1L)
                .query(ServiceNowParams.SYSPARM_FIELDS.getId(), "sys_id")
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

    // *********************************
    // Helpers
    // *********************************

    private String getInstanceName(Map<String, Object> parameters) throws Exception {
        String instanceName = (String)parameters.get("instanceName");
        if (ObjectHelper.isEmpty(instanceName) && ObjectHelper.isNotEmpty(getComponent())) {
            instanceName = getComponent(ServiceNowComponent.class).getInstanceName();
        }

        if (ObjectHelper.isEmpty(instanceName)) {
            throw new NoSuchOptionException("instanceName");
        }

        return instanceName;
    }

    private ServiceNowClient getServiceNowClient(ServiceNowConfiguration configuration, Map<String, Object> parameters) throws Exception {
        ServiceNowClient client = null;

        // check if a client has been supplied to the parameters map
        for (Object value : parameters.values()) {
            if (value instanceof ServiceNowClient) {
                client = ServiceNowClient.class.cast(value);
                break;
            }
        }

        // if no client is provided
        if (ObjectHelper.isEmpty(client)) {
            final String instanceName = getInstanceName(parameters);

            // Configure Api and OAuthToken ULRs using instanceName
            if (!configuration.hasApiUrl()) {
                configuration.setApiUrl(String.format("https://%s.service-now.com/api", instanceName));
            }
            if (!configuration.hasOauthTokenUrl()) {
                configuration.setOauthTokenUrl(String.format("https://%s.service-now.com/oauth_token.do", instanceName));
            }

            client = new ServiceNowClient(getCamelContext(), configuration);
        }

        return client;
    }

    private ServiceNowConfiguration getServiceNowConfiguration(Map<String, Object> parameters) throws Exception {
        ServiceNowConfiguration configuration = null;

        // check if a configuration has been supplied to the parameters map
        for (Object value : parameters.values()) {
            if (value instanceof ServiceNowConfiguration) {
                configuration = ServiceNowConfiguration.class.cast(value);
                break;
            }
        }

        // if no configuration is provided
        if (ObjectHelper.isEmpty(configuration)) {
            if (ObjectHelper.isNotEmpty(getComponent())) {
                configuration = getComponent(ServiceNowComponent.class).getConfiguration().copy();
            } else {
                configuration = new ServiceNowConfiguration();
            }

            // bind parameters to ServiceNow Configuration only if configuration
            // does not come from the parameters map as in that case we expect
            // it to be pre-configured.
            setProperties(configuration, parameters);
        }

        return configuration;
    }
}
