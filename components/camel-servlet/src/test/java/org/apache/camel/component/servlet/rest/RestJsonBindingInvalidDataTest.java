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
package org.apache.camel.component.servlet.rest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.google.gson.JsonSyntaxException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestJsonBindingInvalidDataTest extends ServletCamelRouterTestSupport {

    @Test
    public void testInvalidJson() throws Exception {
        getMockEndpoint("mock:test").expectedMessageCount(0);

        WebRequest req = new PutMethodWebRequest(
                contextUrl + "/services/test/fail",
                new ByteArrayInputStream("This is not JSON format".getBytes()), "application/json");

        WebResponse response = query(req, false);
        assertEquals(400, response.getResponseCode());
        InputStream is = response.getInputStream();
        String data = context.getTypeConverter().convertTo(String.class, is);
        assertEquals("Invalid json data says Camel", data);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration()
                        .component("servlet")
                        // use gson data format
                        .jsonDataFormat("gson")
                        .bindingMode(RestBindingMode.json);

                // catch gson json error so we can return a custom response message
                onException(JsonSyntaxException.class)
                        .handled(true)
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                        .setBody().constant("Invalid json data says Camel");

                rest("/test")
                        .put("/fail").to("mock:test");
            }
        };
    }

}
