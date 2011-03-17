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
package org.apache.camel.processor.resequencer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ResequencerFileNameTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resultEndpoint = getMockEndpoint("mock:result");
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:start").resequence(new MyFileNameExpression()).stream().timeout(100).to("mock:result");
                // END SNIPPET: example
            }
        };
    }

    public void testStreamResequence() throws Exception {
        resultEndpoint.expectedBodiesReceived("20090612-D001", "20090612-D003");
        template.requestBody("direct:start", "20090612-D003");
        template.requestBody("direct:start", "20090612-D001");
        resultEndpoint.assertIsSatisfied();

        resultEndpoint.reset();
        resultEndpoint.expectedBodiesReceived("20090612-D002", "20090615-D001");
        template.requestBody("direct:start", "20090615-D001");
        template.requestBody("direct:start", "20090612-D002");
        resultEndpoint.assertIsSatisfied();
    }
}
