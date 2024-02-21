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

import org.apache.camel.CamelContext;
import org.apache.camel.component.salesforce.api.dto.Limits;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SalesforceJwtBearerTokenFlowTest extends CamelTestSupport {

    @Test
    public void shouldLoginUsingJwtBearerToken() {
        final Limits limits = template.requestBody("salesforce:limits", null, Limits.class);

        assertNotNull(limits);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext camelContext = super.createCamelContext();

        final SalesforceComponent salesforce = new SalesforceComponent();
        final SalesforceLoginConfig loginConfig = LoginConfigHelper.getLoginConfig();

        assumeTrue(loginConfig.getKeystore() != null);
        assumeTrue(loginConfig.getKeystore().getResource() != null);
        // force authentication type to JWT
        loginConfig.setType(AuthenticationType.JWT);

        salesforce.setLoginConfig(loginConfig);

        camelContext.addComponent("salesforce", salesforce);

        return camelContext;
    }
}
