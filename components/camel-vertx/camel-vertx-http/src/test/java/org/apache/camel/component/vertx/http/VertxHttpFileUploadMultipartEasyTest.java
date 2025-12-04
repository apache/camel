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

package org.apache.camel.component.vertx.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.Map;

import jakarta.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class VertxHttpFileUploadMultipartEasyTest extends VertxHttpTestSupport {

    @Test
    public void testVertxFileUpload() {
        File f = new File("src/test/resources/log4j2.properties");

        Exchange out = template.request(
                getProducerUri() + "/upload2?multipartUpload=true&multipartUploadName=cheese", exchange -> {
                    exchange.getMessage().setBody(f);
                });

        assertNotNull(out);
        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("log4j2.properties", out.getMessage().getBody(String.class));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri() + "/upload2").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                        // undertow store the multipart-form as map in the camel message
                        DataHandler dh = (DataHandler)
                                exchange.getMessage().getBody(Map.class).get("cheese");
                        String out = dh.getDataSource().getName();
                        exchange.getMessage().setBody(out);
                    }
                });
            }
        };
    }
}
