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
package org.apache.camel.model;

import java.io.StringWriter;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class GenerateXmFromCamelContextTest extends ContextTestSupport {

    @Test
    public void testCreateRouteFromCamelContext() throws Exception {
        List<RouteDefinition> list = context.getRouteDefinitions();
        assertEquals("Size of list " + list, 1, list.size());
        RouteDefinition routeType = list.get(0);

        log.info("Found route: " + routeType);

        // now lets marshall it!
        dump(routeType);
    }

    protected void dump(Object object) throws Exception {
        JAXBContext jaxbContext = XmlTestSupport.createJaxbContext();
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter buffer = new StringWriter();
        marshaller.marshal(object, buffer);
        log.info("Created: " + buffer);
        assertNotNull(buffer);
        String out = buffer.toString();
        assertTrue("Should contain the description", out.indexOf("<from uri=\"direct:start\"/>") > 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").filter().xpath("/foo/bar = 'abc'").to("mock:result");
            }
        };

    }
}
