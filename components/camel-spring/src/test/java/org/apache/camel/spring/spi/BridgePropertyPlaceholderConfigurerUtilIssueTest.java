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
package org.apache.camel.spring.spi;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class BridgePropertyPlaceholderConfigurerUtilIssueTest extends SpringTestSupport {
    public static final String CONSTANT = "This is the test property.";

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/spi/bridgePropertyPlaceholderConfigurerUtilIssue.xml");
    }

    @Test
    public void testIssue() throws Exception {
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        // we do not receive the constant  but a different value
        result.expectedBodiesReceived("Hello Camel");
        template.sendBody("direct:start", "Test");
        result.assertIsSatisfied();
    }

}
