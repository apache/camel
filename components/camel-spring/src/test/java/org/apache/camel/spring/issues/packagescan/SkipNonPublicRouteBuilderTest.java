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
package org.apache.camel.spring.issues.packagescan;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class SkipNonPublicRouteBuilderTest extends SpringTestSupport {
    
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/issues/packagescan/camelContext.xml");
    }
    
    @Test
    public void testSkipNonPublicRouteBuilder() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").to("mock:bar");
            }
        });
        context.startRoute("bar");
        
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:cool").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        
        template.sendBody("direct:foo", "Hello World");
        template.sendBody("direct:cool", "Bye World");
        template.sendBody("direct:bar", "Hello Camel");
        
        assertMockEndpointsSatisfied();
    }
}
