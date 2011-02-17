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
package org.apache.camel.component.xslt;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 * @version 
 */
@ContextConfiguration
public class XsltXmlFragmentTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected CamelContext camelContext;
    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;

    public void testFragmentPassedToXslt() throws Exception {
        // TODO: See CAMEL-1605. This test is disabled as the bug could highly be outside of Camel
        // mock.expectedMessageCount(1);

        // ProducerTemplate template = camelContext.createProducerTemplate();
        // template.sendBody("direct:test", "<?xml version='1.0'?>\n<root><fragment>insertme!</fragment></root>");

        // MockEndpoint.assertIsSatisfied(camelContext);
    }

}
