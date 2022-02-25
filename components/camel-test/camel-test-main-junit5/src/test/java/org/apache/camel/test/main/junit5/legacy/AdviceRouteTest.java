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
package org.apache.camel.test.main.junit5.legacy;

import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.test.main.junit5.CamelMainTestSupport;
import org.apache.camel.test.main.junit5.common.MyConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A test class ensuring that a route can be advised.
 */
class AdviceRouteTest extends CamelMainTestSupport {

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    protected void configure(MainConfigurationProperties configuration) {
        // Add the configuration class
        configuration.addConfiguration(MyConfiguration.class);
    }

    @Test
    void shouldAdviceTheRoute() throws Exception {
        // Advice the route by replace the from endpoint
        AdviceWith.adviceWith(context, "foo", ad -> ad.replaceFromWith("direct:foo"));

        // must start Camel after we are done using advice-with
        context.start();
        MockEndpoint mock = context.getEndpoint("mock:out", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello Will!");
        String result = template.requestBody("direct:foo", null, String.class);
        mock.assertIsSatisfied();
        assertEquals("Hello Will!", result);
    }
}
