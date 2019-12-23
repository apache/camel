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
package org.apache.camel.spring.processor.scattergather;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

public class ScatterGatherTest extends ContextTestSupport {

    @Test
    public void testScatterAndGather() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        // START SNIPPET: e1
        result.expectedBodiesReceived(1); // expect the lowest quote
        // END SNIPPET: e1

        // START SNIPPET: e2
        Map<String, Object> headers = new HashMap<>();
        headers.put("listOfVendors", "bean:vendor1, bean:vendor2, bean:vendor3");
        headers.put("quoteRequestId", "quoteRequest-1");
        template.sendBodyAndHeaders("direct:start", "<quote_request item=\"beer\"/>", headers);
        // END SNIPPET: e2
        
        result.assertIsSatisfied();
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/scattergather/scatter-gather.xml");
    }
}
