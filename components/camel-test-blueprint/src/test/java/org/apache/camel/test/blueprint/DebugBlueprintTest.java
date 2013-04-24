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
package org.apache.camel.test.blueprint;

import org.junit.Assert;

import org.apache.camel.Exchange;
import org.apache.camel.model.ProcessorDefinition;
import org.junit.Test;

// START SNIPPET: example
// to use camel-test-blueprint, then extend the CamelBlueprintTestSupport class,
// and add your unit tests methods as shown below.
public class DebugBlueprintTest extends CamelBlueprintTestSupport {

    public boolean wasDebugBeforeCalled = false;
    public boolean wasDebugAfterCalled = false;


    // override this method, and return the location of our Blueprint XML file to be used for testing
    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/camelContext.xml";
    }
   
    // here we have regular Junit @Test method
    @Test
    public void testRoute() throws Exception {

        // set mock expectations
        getMockEndpoint("mock:a").expectedMessageCount(1);

        // send a message
        template.sendBody("direct:start", "World");

        // assert mocks
        assertMockEndpointsSatisfied();
        Assert.assertTrue(wasDebugBeforeCalled);
        Assert.assertTrue(wasDebugAfterCalled);
    }


    @Override
    public boolean isUseDebugger() {
        // must enable debugger
        return true;
    }


    @Override
    protected void debugBefore(Exchange exchange, org.apache.camel.Processor processor, ProcessorDefinition<?> definition, String id, String label) {
        log.info("Before " + definition + " with body " + exchange.getIn().getBody());
        wasDebugBeforeCalled = true;
    }


    @Override
    protected void debugAfter(Exchange exchange, org.apache.camel.Processor processor, ProcessorDefinition<?> definition, String id, String label, long timeTaken) {
        log.info("After " + definition + " with body " + exchange.getIn().getBody());
        wasDebugAfterCalled = true;
    }
}
// END SNIPPET: example