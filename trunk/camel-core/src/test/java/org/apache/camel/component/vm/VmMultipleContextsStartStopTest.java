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
package org.apache.camel.component.vm;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class VmMultipleContextsStartStopTest extends AbstractVmTestSupport {

    public void testStartStop() throws Exception {
        /* Check that contexts are communicated */
        MockEndpoint mock = context2.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        template.requestBody("direct:test", "Hello world!");
        mock.assertIsSatisfied();
        mock.reset();
        
        /* Restart the consumer Camel Context */
        context2.stop();
        context2.start();
        
        /* Send a message again and assert that it's received */
        template.requestBody("direct:test", "Hello world!");
        mock.assertIsSatisfied();
        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test").to("vm:foo");
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilderForSecondContext() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:foo").to("mock:result");            
            }
        };
       
    }
    
}