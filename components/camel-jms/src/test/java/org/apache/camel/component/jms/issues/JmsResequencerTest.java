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
package org.apache.camel.component.jms.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 * Unit test for issues CAMEL-1034 and CAMEL-1037
 */
@ContextConfiguration
public class JmsResequencerTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected CamelContext context;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint result;

    protected ProducerTemplate template;
    
    public void setUp() {
        template = context.createProducerTemplate();
    }
    
    public void tearDown() {
        result.reset();
    }
    
    public void testBatchResequencer() throws Exception {
        testResequencer("activemq:queue:in1");
    }
    
    public void testStreamResequencer() throws Exception {
        testResequencer("activemq:queue:in2");
    }
    
    private void testResequencer(String endpoint) throws Exception {
        result.expectedMessageCount(100);
        for (int i = 0; i < 100; i++) {
            result.message(i).body().isEqualTo(Integer.valueOf(i + 1));
        }
        for (int i = 100; i > 0; i--) {
            template.sendBodyAndHeader(endpoint, Integer.valueOf(i), "num", Long.valueOf(i));
        }
        result.assertIsSatisfied();
    }

}
