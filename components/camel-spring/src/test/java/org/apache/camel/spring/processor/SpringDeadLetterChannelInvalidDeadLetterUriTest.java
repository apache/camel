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
package org.apache.camel.spring.processor;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringDeadLetterChannelInvalidDeadLetterUriTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/processor/SpringDeadLetterChannelInvalidDeadLetterUriTest.xml");
    }

    @Override
    protected void setUp() throws Exception {
        try {
            super.setUp();
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            FailedToCreateRouteException ftcre = assertIsInstanceOf(FailedToCreateRouteException.class, e.getCause());
            NoSuchEndpointException cause = assertIsInstanceOf(NoSuchEndpointException.class, ftcre.getCause());
            assertEquals("No endpoint could be found for: xxx, please check your classpath contains the needed Camel component jar.", cause.getMessage());
        }
    }

    @Test
    public void testInvalidUri() throws Exception {
        // noop
    }

}