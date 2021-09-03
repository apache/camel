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
package org.apache.camel.spring.routebuilder;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringRouteTemplateLocalBeanTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/routebuilder/SpringRouteTemplateLocalBeanTest.xml");
    }

    @Test
    public void testLocalBean() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        parameters.put("greeting", "Davs");
        context.addRouteFromTemplate("first", "myTemplate", parameters);

        MockEndpoint mock = getMockEndpoint("mock:cheese");
        mock.expectedBodiesReceived("Davs World");

        template.sendBody("direct:one", "World");

        assertMockEndpointsSatisfied();
    }

}
