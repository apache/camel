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
package org.apache.camel.groovy;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3.x nodes can be marshalled with groovyJson and groovyXml (Jackson 2.x is covered by the other tests).
 */
public class GroovyJackson3DataFormatTest extends CamelTestSupport {

    private static final String BOOK_JSON = """
            {
                "book": {
                    "_id": "bk101",
                    "title": "1984"
                }
            }""";

    private static final String BOOK_XML = """
            <book id="bk101">
              <title>1984</title>
            </book>
            """;

    private final JsonNode book = JsonMapper.builder().build().readTree(BOOK_JSON);

    @Test
    public void testMarshalGroovyJson() {
        Assertions.assertEquals(BOOK_JSON, template.requestBody("direct:json", book, String.class));
    }

    @Test
    public void testMarshalGroovyXml() {
        Assertions.assertEquals(BOOK_XML, template.requestBody("direct:xml", book, String.class));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:json").marshal().groovyJson();
                from("direct:xml").marshal().groovyXml();
            }
        };
    }
}
