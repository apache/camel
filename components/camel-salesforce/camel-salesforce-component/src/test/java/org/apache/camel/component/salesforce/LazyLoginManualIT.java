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

import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.junit.jupiter.api.Test;

public class LazyLoginManualIT extends AbstractSalesforceTestBase {

    @Test
    public void lazyLoginDoesNotThrowExceptions() throws Exception {
        // If we got this far, then createComponent() succeeded without an exception related to lazy login
        // Now we just need to make sure credentials provided after startup work
        final SalesforceLoginConfig config = LoginConfigHelper.getLoginConfig();
        component.getLoginConfig().setLoginUrl(config.getLoginUrl());
        component.getLoginConfig().setClientId(config.getClientId());
        component.getLoginConfig().setClientSecret(config.getClientSecret());
        component.getLoginConfig().setUserName(config.getUserName());
        component.getLoginConfig().setPassword(config.getPassword());
        component.getLoginConfig().setKeystore(config.getKeystore());
        component.getLoginConfig().setRefreshToken(config.getRefreshToken());
        component.getSession().login(null);
    }

    @Override
    protected void createComponent() throws Exception {
        // create the component, but do not set any credentials or login info
        component = new SalesforceComponent();
        component.setLazyLogin(true);

        final SalesforceEndpointConfig config = new SalesforceEndpointConfig();
        config.setApiVersion(System.getProperty("apiVersion", SalesforceEndpointConfig.DEFAULT_VERSION));
        component.setConfig(config);

        HashMap<String, Object> clientProperties = new HashMap<>();
        clientProperties.put("timeout", "60000");
        clientProperties.put("maxRetries", "3");
        // 4MB for RestApiIntegrationTest.testGetBlobField()
        clientProperties.put("maxContentLength", String.valueOf(4 * 1024 * 1024));
        component.setHttpClientProperties(clientProperties);

        // set DTO package
        component.setPackages(Merchandise__c.class.getPackage().getName());

        // add it to context
        context().addComponent("salesforce", component);
    }
}
