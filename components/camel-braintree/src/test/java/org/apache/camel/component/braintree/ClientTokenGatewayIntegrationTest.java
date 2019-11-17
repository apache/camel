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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.ClientTokenGatewayApiMethod;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

public class ClientTokenGatewayIntegrationTest extends AbstractBraintreeTestSupport {
    private static final String PATH_PREFIX = getApiNameAsString(ClientTokenGatewayApiMethod.class);

    @Test
    public void testClientTokenGeneration() throws Exception {
        final String token = requestBody("direct://GENERATE", null, String.class);

        assertTrue(ObjectHelper.isNotEmpty(token));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct://GENERATE")
                    .to("braintree://" + PATH_PREFIX + "/generate");
            }
        };
    }
}
