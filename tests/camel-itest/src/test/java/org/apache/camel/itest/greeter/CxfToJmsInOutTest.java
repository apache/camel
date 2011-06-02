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
package org.apache.camel.itest.greeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class CxfToJmsInOutTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    protected ProducerTemplate template;

    @EndpointInject(uri = "mock:cxf.input")
    protected MockEndpoint inputEndpoint;

    @EndpointInject(uri = "mock:jms.output")
    protected MockEndpoint outputEndpoint;

    @Test
    public void testCxfToJmsInOut() throws Exception {
        assertNotNull(template);
        assertNotNull(inputEndpoint);
        assertNotNull(outputEndpoint);

        inputEndpoint.expectedBodiesReceived("Willem");
        outputEndpoint.expectedBodiesReceived("Hello Willem");

        String out = template.requestBodyAndHeader("cxf://bean:serviceEndpoint", "Willem", CxfConstants.OPERATION_NAME, "greetMe", String.class);
        assertEquals("Hello Willem", out);

        inputEndpoint.assertIsSatisfied();
        outputEndpoint.assertIsSatisfied();
    }

}
