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
package org.apache.camel.component.kestrel;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@Ignore("Manual test as you need to start a Kestrel broker")
public class KestrelSpringXmlTest extends AbstractJUnit4SpringContextTests {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mock;
    
    @EndpointInject(uri = "kestrel://cameltest3")
    private ProducerTemplate producerTemplate;

    @DirtiesContext
    @Test
    public void testProduceAndConsume() throws Exception {
        List<String> bodies = new ArrayList<String>();
        for (int k = 0; k < 10; ++k) {
            bodies.add("this is body #" + k);
        }
        
        mock.expectedMinimumMessageCount(bodies.size());
        mock.expectedBodiesReceivedInAnyOrder(bodies);

        for (String body : bodies) {
            producerTemplate.sendBody(body);
        }

        mock.assertIsSatisfied();
    }
}
