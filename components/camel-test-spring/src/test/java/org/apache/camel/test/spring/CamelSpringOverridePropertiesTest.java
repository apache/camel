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
package org.apache.camel.test.spring;

import java.util.Properties;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;

@RunWith(CamelSpringRunner.class)
@BootstrapWith(CamelTestContextBootstrapper.class)
@ContextConfiguration()
// Put here to prevent Spring context caching across tests and test methods since some tests inherit
// from this test and therefore use the same Spring context.  Also because we want to reset the
// Camel context and mock endpoints between test methods automatically.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CamelSpringOverridePropertiesTest {

    @Produce(uri = "direct:start")
    private ProducerTemplate start;

    @EndpointInject(uri = "mock:a")
    private MockEndpoint mockA;

    @EndpointInject(uri = "mock:test")
    private MockEndpoint mockTest;

    @EndpointInject(uri = "mock:foo")
    private MockEndpoint mockFoo;

    @UseOverridePropertiesWithPropertiesComponent
    public static Properties override() {
        Properties answer = new Properties();
        answer.put("cool.end", "mock:foo");
        return answer;
    }

    @Test
    public void testOverride() throws Exception {
        mockA.expectedBodiesReceived("Camel");
        mockTest.expectedMessageCount(0);
        mockFoo.expectedBodiesReceived("Hello Camel");

        start.sendBody("Camel");

        mockA.assertIsSatisfied();
        mockTest.assertIsSatisfied();
        mockFoo.assertIsSatisfied();
    }

}
