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
package org.apache.camel.component.salesforce;

import java.util.HashMap;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSalesforceTestBase extends CamelTestSupport {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected SalesforceComponent component;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // create the test component
        createComponent();

        return doCreateRouteBuilder();
    }

    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
            }
        };
    }

    protected void createComponent() throws Exception {
        // create the component
        component = new SalesforceComponent();
        final SalesforceEndpointConfig config = new SalesforceEndpointConfig();
        config.setApiVersion(System.getProperty("apiVersion", SalesforceEndpointConfig.DEFAULT_VERSION));
        component.setConfig(config);
        component.setLoginConfig(LoginConfigHelper.getLoginConfig());

        HashMap<String, Object> clientProperties = new HashMap<>();
        clientProperties.put("timeout", "60000");
        clientProperties.put("maxRetries", "3");
        // 4MB for RestApiIntegrationTest.testGetBlobField()
        clientProperties.put("maxContentLength", String.valueOf(4 * 1024 * 1024));
        component.setHttpClientProperties(clientProperties);

        component.setHttpClientIdleTimeout(60000);

        // set DTO package
        component.setPackages(Merchandise__c.class.getPackage().getName());

        // add it to context
        context().addComponent("salesforce", component);
    }
}
