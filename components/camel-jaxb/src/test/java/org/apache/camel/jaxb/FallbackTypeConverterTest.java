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
package org.apache.camel.jaxb;

import java.io.InputStream;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.FallbackTypeConverter;
import org.apache.camel.example.Bar;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FallbackTypeConverterTest extends CamelTestSupport {

    @Test
    void testJaxbFallbackTypeConverter() {
        Bar bar = new Bar();
        bar.setName("camel");
        bar.setValue("cool");
        String result = template.requestBody("direct:start", bar, String.class);
        assertNotNull(result);
        assertTrue(result.indexOf("<bar name=\"camel\" value=\"cool\"") > 0, "Get a wrong xml string");
        assertTrue(result.indexOf("><bar") > 0, "The pretty print setting is not working");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {
                // setup the camel property for the PrettyPrint
                context.getGlobalOptions().put(FallbackTypeConverter.PRETTY_PRINT, "false");

                from("direct:start").process(exchange -> {
                    Message in = exchange.getIn();
                    InputStream is = in.getMandatoryBody(InputStream.class);
                    // make sure we can get the InputStream rightly.
                    exchange.getMessage().setBody(is);
                });
            }
        };
    }
}
