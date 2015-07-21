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
package org.apache.camel.jsonpath;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class JsonPathCharsetTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").transform().jsonpath("$.store.book[*].title").to("mock:authors");
            }
        };
    }

    @Test
    public void testUTF16BEFile() throws Exception {
        getMockEndpoint("mock:authors").expectedMessageCount(1);

        sendBody("direct:start", new File("src/test/resources/booksUTF16BE.json"));

        assertMockEndpointsSatisfied();

        check();
    }

    @Test
    public void testUTF16LEFile() throws Exception {
        getMockEndpoint("mock:authors").expectedMessageCount(1);

        sendBody("direct:start", new File("src/test/resources/booksUTF16LE.json"));

        assertMockEndpointsSatisfied();

        check();
    }

    @Test
    public void testUTF16BEInputStream() throws Exception {
        getMockEndpoint("mock:authors").expectedMessageCount(1);

        InputStream input = JsonPathCharsetTest.class.getClassLoader().getResourceAsStream("booksUTF16BE.json");
        Assert.assertNotNull(input);
        sendBody("direct:start", input);

        assertMockEndpointsSatisfied();

        check();
    }

    @Test
    public void testUTF16BEURL() throws Exception {
        getMockEndpoint("mock:authors").expectedMessageCount(1);

        URL url = new URL("file:src/test/resources/booksUTF16BE.json");
        Assert.assertNotNull(url);
        sendBody("direct:start", url);

        check();
    }

    @Test
    public void testISO8859WithJsonHeaderCamelJsonInputEncoding() throws Exception {
        getMockEndpoint("mock:authors").expectedMessageCount(1);

        URL url = new URL("file:src/test/resources/germanbooks-iso-8859-1.json");
        Assert.assertNotNull(url);
        sendBody("direct:start", url, Collections.<String, Object> singletonMap(JsonPathConstants.HEADER_JSON_ENCODING, "ISO-8859-1"));

        check("Joseph und seine Brüder", "Götzendämmerung");
    }

    private void check() throws InterruptedException {
        check("Sayings of the Century", "Sword of Honour");
    }

    private void check(String title1, String title2) throws InterruptedException {
        assertMockEndpointsSatisfied();

        List<?> authors = getMockEndpoint("mock:authors").getReceivedExchanges().get(0).getIn().getBody(List.class);

        assertEquals(title1, authors.get(0));
        assertEquals(title2, authors.get(1));
    }

}
