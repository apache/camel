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
package org.apache.camel.component.jackson3;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A body that is already the JSON text is written as it is: a file, a stream, bytes, or a String holding a JSON object
 * or array (CAMEL-25329). Any other String and every object is marshalled as before.
 */
public class JacksonMarshalJsonTextTest extends CamelTestSupport {

    private static final String JSON = "{\"sku\": \"CAMEL-MUG\", \"qty\": 2}";

    @TempDir
    static Path dir;

    private String marshal(Object body) {
        Object out = template.requestBody("direct:marshal", body);
        return context.getTypeConverter().convertTo(String.class, out);
    }

    @Test
    public void aStringWithAJsonObjectOrArrayIsWrittenAsIs() {
        assertEquals(JSON, marshal(JSON));
        assertEquals("[" + JSON + "]", marshal("[" + JSON + "]"));
        assertEquals("  " + JSON + "\n", marshal("  " + JSON + "\n"));
    }

    @Test
    public void anyOtherStringIsAJsonString() {
        assertEquals("\"hello\"", marshal("hello"));
        assertEquals("\"{not closed\"", marshal("{not closed"));
    }

    @Test
    public void bytesAndStreamsAreWrittenAsIs() {
        assertEquals(JSON, marshal(JSON.getBytes(StandardCharsets.UTF_8)));
        assertEquals(JSON, marshal(new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void objectsAreMarshalledAsBefore() {
        assertEquals("{\"sku\":\"CAMEL-MUG\"}", marshal(Map.of("sku", "CAMEL-MUG")));
    }

    @Test
    public void aFileIsWrittenAsIs() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:file");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("Content-Type", "application/json");
        // written under another name and moved in, so the consumer never reads a file that is still being written
        Path tmp = Files.writeString(dir.resolve("item.tmp"), JSON);
        Files.move(tmp, dir.resolve("item.json"), StandardCopyOption.ATOMIC_MOVE);

        mock.assertIsSatisfied();
        assertEquals(JSON, mock.getReceivedExchanges().get(0).getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                JacksonDataFormat json = new JacksonDataFormat();
                from("direct:marshal").marshal(json);
                from("file:" + dir + "?noop=true&include=.*\\.json&initialDelay=0&delay=10").marshal(json).to("mock:file");
            }
        };
    }
}
