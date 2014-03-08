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
package org.apache.camel.cdi;

import javax.inject.Inject;

import org.apache.camel.Consume;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Tests sending & consuming to a stubbed out component called 'cheese' which is created
 * via the {@link org.apache.camel.cdi.support.CheeseComponentFactory} class
 */
public class ConsumeStubbedEndpointTest extends CdiTestSupport {

    @Inject @Mock
    private MockEndpoint result;

    @Inject @Uri("cheese:start")
    private ProducerTemplate producer;

    @Consume(uri = "cheese:start")
    public void onStart(String body) {
        producer.sendBody("mock:result", "Hello " + body + "!");
    }

    @Test
    public void consumeAnnotation() throws Exception {
        assertNotNull("Could not inject producer", producer);
        assertNotNull("Could not inject mock endpoint", result);

        result.expectedBodiesReceived("Hello world!");

        producer.sendBody("world");

        result.assertIsSatisfied();
    }

}
