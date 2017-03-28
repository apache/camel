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


import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.hello_world_soap_http.PingMeFault;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@ContextConfiguration
public class CamelGreeterConsumerTest extends AbstractJUnit4SpringContextTests {
    private static int port = AvailablePortFinder.getNextAvailable(20001);
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("CamelGreeterConsumerTest.port", Integer.toString(port));
    }
 
    @Autowired
    protected CamelContext camelContext;

    @Test
    public void testInvokeServers() throws Exception {
        assertNotNull(camelContext);

        ProducerTemplate template = camelContext.createProducerTemplate();
        List<String> params = new ArrayList<String>();
        params.add("Willem");
        Object result = template.sendBodyAndHeader("cxf://bean:serviceEndpoint", ExchangePattern.InOut,
                                                   params, CxfConstants.OPERATION_NAME, "greetMe");
        assertTrue("Result is a list instance ", result instanceof List);
        assertEquals("Get the wrong response", ((List<?>)result).get(0), "HelloWillem");
        try {
            template.sendBodyAndHeader("cxf://bean:serviceEndpoint", ExchangePattern.InOut,
                                            params, CxfConstants.OPERATION_NAME, "pingMe");
            fail("Expect exception here.");
        } catch (Exception ex) {
            assertTrue("Get a wrong exception.", ex instanceof CamelExecutionException);
            assertTrue("Get a wrong exception cause. ", ex.getCause() instanceof PingMeFault);
        }
        template.stop();
    }

}
