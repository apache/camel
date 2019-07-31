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
package org.apache.camel.component.braintree;

import java.util.List;

import com.braintreegateway.Plan;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.PlanGatewayApiMethod;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlanGatewayIntegrationTest extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(PlanGatewayIntegrationTest.class);
    private static final String PATH_PREFIX = getApiNameAsString(PlanGatewayApiMethod.class);

    @Ignore
    @Test
    public void testAll() throws Exception {
        final List<Plan> result = requestBody("direct://ALL", null, List.class);

        assertNotNull("all result", result);
        LOG.debug("all: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for all
                from("direct://ALL")
                    .to("braintree://" + PATH_PREFIX + "/all");
            }
        };
    }
}
