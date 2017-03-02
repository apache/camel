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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.servicenow.model.Incident;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class ServiceNowBlueprintComponentAuthTest extends CamelBlueprintTestSupport {
    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/blueprint-component-auth.xml";
    }

    @Test
    public void testAuth() throws Exception {
        MockEndpoint mock1 = getMockEndpoint("mock:servicenow-1");
        mock1.expectedMessageCount(1);
        MockEndpoint mock2 = getMockEndpoint("mock:servicenow-2");
        mock2.expectedMessageCount(1);

        template().sendBodyAndHeaders(
            "direct:servicenow",
            null,
            ServiceNowTestSupport.kvBuilder()
                .put(ServiceNowConstants.RESOURCE, "table")
                .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                .put(ServiceNowParams.SYSPARM_LIMIT, 10)
                .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                .build()
        );

        mock1.assertIsSatisfied();
        mock2.assertIsSatisfied();

        validate(mock1.getExchanges().get(0));
        validate(mock2.getExchanges().get(0));
    }

    private void validate(Exchange exchange) {
        List<Incident> items = exchange.getIn().getBody(List.class);

        assertNotNull(items);
        assertTrue(items.size() <= 10);
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.OFFSET_FIRST));
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.OFFSET_NEXT));
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.OFFSET_LAST));
    }
}
