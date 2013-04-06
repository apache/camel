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
package org.apache.camel.impl;

import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;

/**
 * Unit test of the gzip data format.
 */
public class GzipDataFormatTest extends ContextTestSupport {
    private static final String TEXT = "Hamlet by William Shakespeare\n"
            + "To be, or not to be: that is the question:\n"
            + "Whether 'tis nobler in the mind to suffer\n"
            + "The slings and arrows of outrageous fortune,\n"
            + "Or to take arms against a sea of troubles,\n"
            + "And by opposing end them? To die: to sleep;";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private byte[] sendText() throws Exception {
        return (byte[]) template.requestBody("direct:start", TEXT.getBytes("UTF-8"));
    }

    public void testMarshalTextToGZip() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().gzip();
            }
        });
        context.start();

        byte[] output = sendText();

        GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(output));
        String result = IOConverter.toString(stream, null);
        assertEquals("Uncompressed something different than compressed", TEXT, result);
    }

    public void testUnMarshalTextToGzip() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().gzip().unmarshal().gzip().to("mock:result");
            }
        });
        context.start();

        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(TEXT.getBytes("UTF-8"));
        sendText();
        result.assertIsSatisfied();
    }
}
