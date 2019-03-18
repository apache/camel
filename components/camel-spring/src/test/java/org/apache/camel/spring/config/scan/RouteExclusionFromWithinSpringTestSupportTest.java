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
package org.apache.camel.spring.config.scan;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.spring.config.scan.route.MyExcludedRouteBuilder;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RouteExclusionFromWithinSpringTestSupportTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(new String[] {"org/apache/camel/spring/config/scan/componentScan.xml"}, getRouteExcludingApplicationContext());
    }

    @Test
    public void testRouteExcluded() throws InterruptedException {
        assertEquals(1, context.getRoutes().size());
        MockEndpoint mock = getMockEndpoint("mock:definitelyShouldNeverReceiveExchange");
        mock.expectedMessageCount(0);

        sendBody("seda:shouldNeverRecieveExchange", "dropped like a hot rock");
        mock.await(500, TimeUnit.MILLISECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    public void testRoutesNotExcludedWorkNormally() throws InterruptedException {
        template.sendBody("direct:start", "request");
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
    }

    @Override
    protected Class<?> excludeRoute() {

        return MyExcludedRouteBuilder.class;
    }

}
