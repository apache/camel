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
package org.apache.camel.component.restlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.restlet.data.MediaType.APPLICATION_OCTET_STREAM;
import static org.restlet.data.MediaType.AUDIO_MPEG;

/**
 * @version
 */
public class RestletProducerBinaryStreamTest extends RestletTestSupport {

    @Test
    public void shouldHandleBinaryOctetStream() throws Exception {
        Exchange response = template.request("restlet:http://localhost:" + portNum + "/application/octet-stream?streamRepresentation=true", null);

        assertThat(response.getOut().getHeader(CONTENT_TYPE, String.class), equalTo("application/octet-stream"));
        assertThat(response.getOut().getBody(byte[].class), equalTo(getAllBytes()));
    }

    @Test
    public void shouldHandleBinaryAudioMpeg() throws Exception {
        Exchange response = template.request("restlet:http://localhost:" + portNum + "/audio/mpeg?streamRepresentation=true", null);

        assertThat(response.getOut().getHeader(CONTENT_TYPE, String.class), equalTo("audio/mpeg"));
        assertThat(response.getOut().getBody(byte[].class), equalTo(getAllBytes()));
    }

    @Test
    public void shouldAutoClose() throws Exception {
        Exchange response = template.request("restlet:http://localhost:" + portNum + "/application/octet-stream?streamRepresentation=true&autoCloseStream=true", null);

        assertThat(response.getOut().getHeader(CONTENT_TYPE, String.class), equalTo("application/octet-stream"));
        InputStream is = (InputStream) response.getOut().getBody();
        assertNotNull(is);

        try {
            is.read();
            fail("Should be closed");
        } catch (IOException e) {
            // expected
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:" + portNum + "/application/octet-stream")
                        .setHeader(CONTENT_TYPE, constant(APPLICATION_OCTET_STREAM))
                        .setBody(constant(new ByteArrayInputStream(getAllBytes())));

                from("restlet:http://localhost:" + portNum + "/audio/mpeg")
                        .setHeader(CONTENT_TYPE, constant(AUDIO_MPEG))
                        .setBody(constant(new ByteArrayInputStream(getAllBytes())));
            }
        };
    }

    private static byte[] getAllBytes() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) {
            data[i] = (byte) (Byte.MIN_VALUE + i);
        }
        return data;
    }
}
