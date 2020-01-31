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
package org.apache.camel.component.workday;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link WorkdayEndpoint}.
 */
@Component("workday")
public class WorkdayComponent extends DefaultComponent {

    public static final String RAAS_ENDPOINT_URL = "https://%s/ccx/service/customreport2/%s%s";

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        WorkdayEndpoint endpoint = new WorkdayEndpoint(uri, this);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters) throws Exception {

        WorkdayEndpoint workdayEndpoint = (WorkdayEndpoint)endpoint;
        WorkdayConfiguration workdayConfiguration = workdayEndpoint.getWorkdayConfiguration();

        validateRequiredParameters(workdayConfiguration);

        StringBuilder stringBuilder = new StringBuilder(remaining);
        stringBuilder.append("?");

        if (parameters.size() > 0) {

            String params = parameters.keySet().stream().map(k -> k + "=" + parameters.get(k)).collect(Collectors.joining("&"));
            stringBuilder.append(params);
            stringBuilder.append("&");
        }

        stringBuilder.append("format=");
        stringBuilder.append(workdayConfiguration.getFormat());

        String uriString = String.format(RAAS_ENDPOINT_URL, workdayConfiguration.getHost(), workdayConfiguration.getTenant(), stringBuilder.toString());

        workdayEndpoint.setUri(uriString);

    }

    protected void validateRequiredParameters(WorkdayConfiguration workdayConfiguration) {

        if (workdayConfiguration.getHost() == null || workdayConfiguration.getTenant() == null || workdayConfiguration.getClientId() == null
            || workdayConfiguration.getClientSecret() == null || workdayConfiguration.getTokenRefresh() == null) {

            throw new ResolveEndpointFailedException("The parameters host, tenant, clientId, clientSecret and tokenRefresh are required.");
        }
    }
}
