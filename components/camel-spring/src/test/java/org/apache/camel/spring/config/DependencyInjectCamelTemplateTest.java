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
package org.apache.camel.spring.config;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class DependencyInjectCamelTemplateTest extends SpringRunWithTestSupport {
    protected String body = "Hello";

    @Autowired
    private TemplateUsingBean bean;

    @EndpointInject("mock:results")
    private MockEndpoint endpoint;

    @Test
    public void testBeanHasCamelTemplateInjected() throws Exception {
        assertNotNull("Bean should be injected", bean);
        ProducerTemplate template = bean.getTemplate();
        assertNotNull("Bean should have a CamelTemplate", template);

        endpoint.expectedBodiesReceived(body);

        template.sendBody(body);

        endpoint.assertIsSatisfied();
    }

}
