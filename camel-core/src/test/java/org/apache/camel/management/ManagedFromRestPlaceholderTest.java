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
package org.apache.camel.management;

import java.util.Arrays;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.RestParamType;

public class ManagedFromRestPlaceholderTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("dummy-test", new DummyRestConsumerFactory());

        CamelContext answer = new DefaultCamelContext(registry);

        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("classpath:org/apache/camel/management/rest.properties");
        answer.addComponent("properties", pc);

        return answer;
    }

    public void testFromRestModelPlaceholder() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=context,name=\"camel-1\"");

        String xml = (String) mbeanServer.invoke(on, "dumpRestsAsXml", new Object[]{true}, new String[]{"boolean"});
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<rests"));
        assertTrue(xml.contains("<rest path=\"/say/hello\">"));
        assertTrue(xml.contains("<rest path=\"/say/bye\">"));
        assertTrue(xml.contains("</rest>"));
        assertTrue(xml.contains("<get"));
        assertTrue(xml.contains("application/json"));
        assertTrue(xml.contains("<post"));
        assertTrue(xml.contains("application/json"));
        assertTrue(xml.contains("</rests>"));

        assertTrue(xml.contains("<param collectionFormat=\"multi\" dataType=\"string\" defaultValue=\"b\" description=\"header param description2\" "
                + "name=\"header_letter\" required=\"false\" type=\"query\">"));
        assertTrue(xml.contains("<param dataType=\"integer\" defaultValue=\"1\" description=\"header param description1\" "
                + "name=\"header_count\" required=\"true\" type=\"header\">"));
        assertTrue(xml.contains("<value>1</value>"));
        assertTrue(xml.contains("<value>a</value>"));

        assertTrue(xml.contains("<responseMessage code=\"300\" message=\"test msg\" responseModel=\"java.lang.Integer\"/>"));

        String xml2 = (String) mbeanServer.invoke(on, "dumpRoutesAsXml", null, null);
        log.info(xml2);
        // and we should have rest in the routes that indicate its from a rest dsl
        assertTrue(xml2.contains("rest=\"true\""));

        // there should be 3 + 2 routes
        assertEquals(3 + 2, context.getRouteDefinitions().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost");
                rest("{{foo}}")
                    .get().to("direct:hello");

                rest("{{bar}}")
                    .get().consumes("application/json")
                        .param().type(RestParamType.header).description("header param description1").dataType("integer").allowableValues(Arrays.asList("1", "2", "3", "4"))
                            .defaultValue("1").name("header_count").required(true)
                        .endParam().
                        param().type(RestParamType.query).description("header param description2").dataType("string").allowableValues(Arrays.asList("a", "b", "c", "d"))
                            .defaultValue("b").collectionFormat(CollectionFormat.multi).name("header_letter").required(false)
                        .endParam()
                        .responseMessage().code(300).message("test msg").responseModel(Integer.class).endResponseMessage()
                        .to("direct:bye")
                    .post().to("mock:update");

                from("direct:hello")
                    .transform().constant("Hello World");

                from("direct:bye")
                    .transform().constant("Bye World");
            }
        };
    }
}
