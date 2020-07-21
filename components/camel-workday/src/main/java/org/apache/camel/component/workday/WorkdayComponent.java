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

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the component that manages {@link WorkdayEndpoint}.
 */
@Component("workday")
public class WorkdayComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        WorkdayConfiguration configuration = new WorkdayConfiguration();

        configuration = parseConfiguration(configuration, remaining, parameters);

        WorkdayEndpoint endpoint = new WorkdayEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters) throws Exception {
        WorkdayEndpoint workdayEndpoint = (WorkdayEndpoint)endpoint;
        WorkdayConfiguration workdayConfiguration = workdayEndpoint.getWorkdayConfiguration();
        validateConnectionParameters(workdayConfiguration);
    }

    /**
     * Parses the configuration
     * @return the parsed and valid configuration to use
     */
    protected WorkdayConfiguration parseConfiguration(WorkdayConfiguration configuration, String remaining, Map<String, Object> parameters) throws Exception {
        configuration.parseURI(remaining, parameters);
        return configuration;
    }

    protected void validateConnectionParameters(WorkdayConfiguration workdayConfiguration) {
        ObjectHelper.notNull(workdayConfiguration.getHost(), "Host");
        ObjectHelper.notNull(workdayConfiguration.getTenant(), "Tenant");
        ObjectHelper.notNull(workdayConfiguration.getClientId(), "ClientId");
        ObjectHelper.notNull(workdayConfiguration.getClientSecret(), "ClientSecret");
        ObjectHelper.notNull(workdayConfiguration.getTokenRefresh(), "TokenRefresh");
    }
}
