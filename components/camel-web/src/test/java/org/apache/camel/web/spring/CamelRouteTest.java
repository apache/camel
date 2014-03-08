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
package org.apache.camel.web.spring;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;

import org.apache.camel.CamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.test.junit4.TestSupport;
import org.apache.camel.web.resources.CamelContextResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * @version 
 */
public class CamelRouteTest extends TestSupport {
    protected AbstractXmlApplicationContext applicationContext;
    protected CamelContext camelContext;
 
    @Test
    public void testCanMarshalRoutes() throws Exception {
        CamelContextResource resource = new CamelContextResource(camelContext);
        RoutesDefinition routes = resource.getRoutesResource().getRouteDefinitions();
        List<RouteDefinition> list = routes.getRoutes();
        log.info("Found routes: " + list);

        // now lets marshall to XML
        JAXBContext context = JAXBContext.newInstance(RoutesDefinition.class.getPackage().getName());
        StringWriter out = new StringWriter();
        context.createMarshaller().marshal(routes, out);
        String xml = out.toString();
        log.info("XML is: " + xml);
    }

    @Before
    public void setUp() throws Exception {
        applicationContext = new FileSystemXmlApplicationContext("src/main/webapp/WEB-INF/applicationContext.xml");
        applicationContext.start();
        Map<String, CamelContext> beansOfType = applicationContext.getBeansOfType(CamelContext.class);
        camelContext = beansOfType.isEmpty() ? null : beansOfType.values().iterator().next();
        assertNotNull("camelContext", camelContext);
    }

    @After
    public void tearDown() throws Exception {
        if (applicationContext != null) {
            applicationContext.stop();
        }
    }
}
