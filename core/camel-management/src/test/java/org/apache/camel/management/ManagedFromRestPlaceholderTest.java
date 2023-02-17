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
package org.apache.camel.management;

import java.util.Arrays;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.RestParamType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedFromRestPlaceholderTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        answer.getRegistry().bind("dummy-test", new DummyRestConsumerFactory());
        answer.getPropertiesComponent().setLocation("classpath:org/apache/camel/management/rest.properties");
        return answer;
    }

    @Test
    public void testFromRestModelPlaceholder() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        String xml = (String) mbeanServer.invoke(on, "dumpRestsAsXml", new Object[] { true }, new String[] { "boolean" });
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

        assertTrue(xml.contains("<param defaultValue=\"1\" dataType=\"integer\" name=\"header_count\""
                                + " description=\"header param description1\" type=\"header\" required=\"true\">"));
        assertTrue(xml.contains("<param defaultValue=\"b\" dataType=\"string\" name=\"header_letter\""
                                + " description=\"header param description2\" type=\"query\" collectionFormat=\"multi\" required=\"false\">"));
        assertTrue(xml.contains("<value>1</value>"));
        assertTrue(xml.contains("<value>a</value>"));

        assertTrue(xml.contains("<responseMessage code=\"300\" responseModel=\"java.lang.Integer\" message=\"test msg\"/>"));

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
                        .param().type(RestParamType.header).description("header param description1").dataType("integer")
                        .allowableValues(Arrays.asList("1", "2", "3", "4"))
                        .defaultValue("1").name("header_count").required(true)
                        .endParam().param().type(RestParamType.query).description("header param description2")
                        .dataType("string").allowableValues(Arrays.asList("a", "b", "c", "d"))
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
