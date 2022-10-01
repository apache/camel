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
package org.apache.camel.jsonpath;

import java.io.File;
import java.util.List;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonPathTransformONielPlaceholderTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = context.getPropertiesComponent();
        Properties props = new Properties();
        props.put("who", "John O'Niel");
        props.put("search", "Sword's of Honour");
        pc.setInitialProperties(props);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .transform().jsonpath("$.store.book[?(@.author == '{{who}}' || @.title == '{{search}}')].title")
                        .to("mock:authors");
            }
        };
    }

    @Test
    public void testAuthors() throws Exception {
        getMockEndpoint("mock:authors").expectedMessageCount(1);

        template.sendBody("direct:start", new File("src/test/resources/books.json"));

        MockEndpoint.assertIsSatisfied(context);

        List<?> titles = getMockEndpoint("mock:authors").getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(2, titles.size());
        assertEquals("Sword's of Honour", titles.get(0));
        assertEquals("Camels in Space", titles.get(1));
    }

}
