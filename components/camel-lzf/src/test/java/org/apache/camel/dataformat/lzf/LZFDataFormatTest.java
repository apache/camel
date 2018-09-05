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
package org.apache.camel.dataformat.lzf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.ning.compress.lzf.LZFInputStream;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class LZFDataFormatTest extends CamelTestSupport {
    private static final String TEXT = "Hamlet by William Shakespeare\n"
            + "To be, or not to be: that is the question:\n"
            + "Whether 'tis nobler in the mind to suffer\n"
            + "The slings and arrows of outrageous fortune,\n"
            + "Or to take arms against a sea of troubles,\n"
            + "And by opposing end them? To die: to sleep;";

    @Test
    public void testMarshalTextToLzf() throws Exception {
        byte[] output = sendText("direct:textToLzf");

        InputStream stream = new LZFInputStream(new ByteArrayInputStream(output));
        String result = IOConverter.toString(stream, null);
        assertEquals("Uncompressed something different than compressed", TEXT, result);
    }

    @Test
    public void testUnMarshalTextToLzf() throws Exception {
        MockEndpoint result = context.getEndpoint("mock:unMarshalTextToLzf", MockEndpoint.class);
        result.expectedBodiesReceived(TEXT.getBytes("UTF-8"));

        sendText("direct:unMarshalTextToLzf");

        result.assertIsSatisfied();
    }

    @Test
    public void testParallelUnMarshalTextToLzf() throws Exception {
        MockEndpoint result = context.getEndpoint("mock:parallelUnMarshalTextToLzf", MockEndpoint.class);
        result.expectedBodiesReceived(TEXT.getBytes("UTF-8"));

        sendText("direct:parallelUnMarshalTextToLzf");

        result.assertIsSatisfied();
    }

    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                LZFDataFormat dataFormat = new LZFDataFormat();
                dataFormat.setUsingParallelCompression(true);

                from("direct:textToLzf")
                    .marshal().lzf();
                from("direct:unMarshalTextToLzf")
                    .marshal().lzf()
                    .unmarshal().lzf()
                    .to("mock:unMarshalTextToLzf");
                from("direct:parallelUnMarshalTextToLzf")
                    .marshal(dataFormat)
                    .unmarshal(dataFormat)
                    .to("mock:parallelUnMarshalTextToLzf");
            }
        };
    }

    private byte[] sendText(String destination) throws Exception {
        return (byte[]) template.requestBody(destination, TEXT.getBytes("UTF-8"));
    }
}
