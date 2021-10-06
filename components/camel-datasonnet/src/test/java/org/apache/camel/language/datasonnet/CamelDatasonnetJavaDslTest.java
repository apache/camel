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

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:basicTransform")
                        .routeId("basicTransform")
                        .setProperty("test", constant("HelloWorld"))
                        .setProperty("count", simple("1").resultType(Integer.class))
                        .setProperty("isActive", simple("true").resultType(Boolean.class))
                        .setProperty("1. Full Name", constant("DataSonnet"))
                        .transform(datasonnet("resource:classpath:simpleMapping.ds", String.class)
                                .bodyMediaType(MediaTypes.APPLICATION_JSON_VALUE)
                                .outputMediaType(MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:direct:end");

                from("direct:transformXML")
                        .routeId("transformXML")
                        .transform(datasonnet("resource:classpath:readXMLExtTest.ds", String.class)
                                .bodyMediaType(MediaTypes.APPLICATION_XML_VALUE)
                                .outputMediaType(MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:direct:end");

                from("direct:transformCSV")
                        .routeId("transformCSV")
                        .transform(datasonnet("resource:classpath:readCSVTest.ds", String.class)
                                .bodyMediaType(MediaTypes.APPLICATION_CSV_VALUE)
                                .outputMediaType(MediaTypes.APPLICATION_JSON_VALUE))
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
