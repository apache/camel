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

package org.apache.camel.test.blueprint.cloud;

import org.apache.camel.Exchange;
import org.apache.camel.impl.cloud.ServiceCallConstants;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class ServiceCallFilterTest extends CamelBlueprintTestSupport {
    @Test
    public void testServiceFilter() throws Exception {
        Exchange result;

        result = template.request("direct:start", e -> { 
            return; 
        });

        assertHeader(result, ServiceCallConstants.SERVICE_HOST, "host1");
        assertHeader(result, ServiceCallConstants.SERVICE_PORT, 9093);

        result = template.request("direct:start", e -> { 
            return;
        });

        assertHeader(result, ServiceCallConstants.SERVICE_HOST, "host4");
        assertHeader(result, ServiceCallConstants.SERVICE_PORT, 9094);
    }

    // *********************
    // Helpers
    // *********************

    private void assertHeader(Exchange exchange, String header, Object expectedValue) {
        Assert.assertNotNull(exchange);
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(header));
        Assert.assertEquals(expectedValue, exchange.getIn().getHeader(header));
    }

    // *********************
    // Blueprint
    // *********************

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/cloud/ServiceCallFilterTest.xml";
    }
}
