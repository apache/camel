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
package org.apache.camel.language.datasonnet;

import java.io.InputStream;
import java.nio.charset.Charset;

import com.datasonnet.document.Document;
import com.datasonnet.document.MediaTypes;
import library.TestLib;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class CamelDatasonnetJavaDslTest extends CamelTestSupport {
    private MockEndpoint mock;

    @BindToRegistry
    public TestLib testLib() {
        return TestLib.getInstance();
    }

    @Test
    public void testTransform() throws Exception {
        runCamelTest(loadResourceAsString("simpleMapping_payload.json"),
                loadResourceAsString("simpleMapping_result.json"),
                "direct:basicTransform");
    }

    @Test
    public void testTransformXML() throws Exception {
        runCamelTest(loadResourceAsString("payload.xml"),
                loadResourceAsString("readXMLExtTest.json"),
                "direct:transformXML");
    }

    @Test
    public void testTransformCSV() throws Exception {
        runCamelTest(loadResourceAsString("payload.csv"),
                "{\"account\":\"123\"}",
                "direct:transformCSV");
    }

    @Test
    public void testRegistryLibraries() throws Exception {
        runCamelTest("{}",
                "{ \"test\":\"Hello, World\"}",
                "direct:registryLibraries");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:basicTransform")
                        .routeId("basicTransform")
                        .setProperty("test", constant("HelloWorld"))
                        .setProperty("count", simple("1", Integer.class))
                        .setProperty("isActive", simple("true", Boolean.class))
                        .setProperty("1. Full Name", constant("DataSonnet"))
                        .transform(datasonnet("resource:classpath:simpleMapping.ds", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:direct:end");

                from("direct:transformXML")
                        .routeId("transformXML")
                        .transform(datasonnet("resource:classpath:readXMLExtTest.ds", String.class,
                                MediaTypes.APPLICATION_XML_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:direct:end");

                from("direct:transformCSV")
                        .routeId("transformCSV")
                        .transform(datasonnet("resource:classpath:readCSVTest.ds", String.class,
                                MediaTypes.APPLICATION_CSV_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:direct:end");

                from("direct:registryLibraries")
                        .routeId("registryLibraries")
                        .transform(datasonnet("{test: testlib.sayHello()}", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:direct:end");

            }
        };
    }

    private void runCamelTest(Object payload, String expectedJson, String uri) throws Exception {
        template.sendBody(uri, payload);
        mock = getMockEndpoint("mock:direct:end");
        Exchange exchange = mock.assertExchangeReceived(mock.getReceivedCounter() - 1);
        Object body = exchange.getMessage().getBody();
        String response;
        if (body instanceof Document) {
            response = ExchangeHelper.convertToMandatoryType(exchange, String.class, ((Document<?>) body).getContent());
        } else {
            response = exchange.getMessage().getBody(String.class);

        }
        JSONAssert.assertEquals(expectedJson, response, true);
    }

    private String loadResourceAsString(String name) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(name);
        return IOUtils.toString(is, Charset.defaultCharset());
    }
}
