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
package org.apache.camel.spring.example;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;

/**
 * @version 
 */
public class ContentBasedRouteTest extends SpringTestSupport {
    protected MockEndpoint matchedEndpoint;
    protected MockEndpoint notMatchedEndpoint;
    protected Object body = "<hello>world!</hello>";
    protected String header = "destination";

    public void testMatchesPredicate() throws Exception {
        matchedEndpoint.expectedMessageCount(1);
        notMatchedEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", body, header, "firstChoice");

        assertIsSatisfied(matchedEndpoint, notMatchedEndpoint);
    }

    public void testDoesNotMatchPredicate() throws Exception {
        matchedEndpoint.expectedMessageCount(0);
        notMatchedEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", body, header, "notMatchedValue");

        assertIsSatisfied(matchedEndpoint, notMatchedEndpoint);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        matchedEndpoint = getMockEndpoint("mock:matched");
        notMatchedEndpoint = getMockEndpoint("mock:notMatched");
    }

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/example/contentBasedRoute.xml");
    }
}
