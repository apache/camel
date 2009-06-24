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
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@ContextConfiguration
public class CamelGreeterConsumerTest extends AbstractJUnit4SpringContextTests {
    private static final transient Log LOG = LogFactory.getLog(CamelGreeterTest.class);

    @Autowired
    protected CamelContext camelContext;

    @Test
    public void testMocksAreValid() throws Exception {
        assertNotNull(camelContext);

        ProducerTemplate template = camelContext.createProducerTemplate();
        List<String> params = new ArrayList<String>();
        params.add("Willem");
        Object result = template.sendBodyAndHeader("cxf://bean:serviceEndpoint", ExchangePattern.InOut , 
                                                   params, CxfConstants.OPERATION_NAME, "greetMe");
        assertTrue("Result is a list instance ", result instanceof List);
        assertEquals("Get the wrong response", ((List)result).get(0), "HelloWillem");

        template.stop();
    }

}
