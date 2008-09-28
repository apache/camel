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
package org.apache.camel.rest.spring;

import java.io.StringWriter;
import java.util.List;

import javax.xml.bind.JAXBContext;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.RoutesType;
import org.apache.camel.rest.resources.CamelContextResource;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * @version $Revision$
 */
public class CamelRouteTest extends TestCase {
    protected AbstractXmlApplicationContext applicationContext;
    protected CamelContext camelContext;

    public void testCanMarshalRoutes() throws Exception {
        CamelContextResource resource = new CamelContextResource(camelContext);
        RoutesType routes = resource.getRouteDefinitions();
        List<RouteType> list = routes.getRoutes();
        System.out.println("Found routes: " + list);

        // now lets marshall to XML
        JAXBContext context = JAXBContext.newInstance(RoutesType.class.getPackage().getName());
        StringWriter out = new StringWriter();
        context.createMarshaller().marshal(routes, out);
        String xml = out.toString();
        System.out.println("XML is: " + xml);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        applicationContext = new FileSystemXmlApplicationContext("src/main/webapp/WEB-INF/applicationContext.xml");
        applicationContext.start();
        camelContext = (CamelContext) applicationContext.getBean("camelContext", CamelContext.class);
        assertNotNull("camelContext", camelContext);
    }

    @Override
    protected void tearDown() throws Exception {
        if (applicationContext != null) {
            applicationContext.stop();
        }
        super.tearDown();
    }
}
