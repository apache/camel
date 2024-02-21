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
package org.apache.camel.routepolicy.quartz;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.test.junit5.TestSupport.executeSlowly;

public class SpringMultiplePoliciesOnRouteTest extends CamelSpringTestSupport {
    private String url = "seda:foo?concurrentConsumers=20";
    private int size = 100;

    @Test
    public void testMultiplePoliciesOnRoute() throws Exception {
        // we use seda which are not persistent and hence can loose a message
        // when we get graceful shutdown support we can prevent this
        getMockEndpoint("mock:success").expectedMinimumMessageCount(size - 10);

        executeSlowly(size, 3, TimeUnit.MILLISECONDS, (i) -> template.sendBody(url, "Message " + i));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz/MultiplePolicies.xml");
    }

}
