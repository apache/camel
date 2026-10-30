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
package org.apache.camel.component.langchain4j.tokenizer;

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.TokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jTokenizerDefinition;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The body types of the tokenizer EIP (CAMEL-25928), checked at runtime: it reads the body as text and leaves the
 * chunks of the text as a String[].
 */
public class TokenizerBodyTypesTest extends LangChain4JTokenizerTestSupport {

    @Test
    public void textGivesTheChunksAsAnArray() {
        BodyTypes bodyTypes = TokenizerDefinition.class.getAnnotation(BodyTypes.class);
        assertNotNull(bodyTypes);
        assertEquals(Carrier.TEXT, bodyTypes.eipIn().value()[0]);
        BodyType out = bodyTypes.eipOut();
        assertEquals(Carrier.LIST, out.value()[0]);
        assertEquals("java.lang.String[]", out.javaType());

        Exchange exchange = template.send("direct:start", e -> e.getMessage().setBody(TEXT));
        assertNull(exchange.getException());
        Object body = exchange.getMessage().getBody();
        assertInstanceOf(String[].class, body);
        assertEquals(out.javaType(), body.getClass().getCanonicalName());
        assertEquals(4, ((String[]) body).length);
    }

    @Test
    public void bytesAreReadAsText() {
        // the tokenizer reads the body with getBody(String.class), so bytes are converted to text first
        Exchange exchange
                = template.send("direct:start", e -> e.getMessage().setBody(TEXT.getBytes(StandardCharsets.UTF_8)));
        assertNull(exchange.getException());
        Exchange text = template.send("direct:start", e -> e.getMessage().setBody(TEXT));
        assertArrayEquals((String[]) text.getMessage().getBody(), (String[]) exchange.getMessage().getBody());
    }

    @Test
    public void anObjectNoConverterKnowsIsReadAsItsToString() {
        // no converter knows Order, but the fallback converter to String uses toString(), so the object is not
        // refused: its toString() is what is split into chunks
        Order order = new Order();
        Exchange exchange = template.send("direct:start", e -> e.getMessage().setBody(order));
        assertNull(exchange.getException());
        assertArrayEquals(new String[] { order.toString() }, (String[]) exchange.getMessage().getBody());
    }

    @Test
    public void nullBodyCompletesWithException() {
        // CAMEL-26301: a null body must not hang the exchange forever — the AsyncCallback must always be called.
        // The tokenizer throws on null body; TokenizerProcessor must catch it, set it on the exchange,
        // and signal the callback so the error handler can deal with it normally.
        Exchange exchange = template.send("direct:start", e -> e.getMessage().setBody(null));
        assertNotNull(exchange.getException(), "exchange must have an exception, not hang");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .tokenize(tokenizer()
                                .byParagraph()
                                .maxSegmentSize(1024)
                                .maxOverlap(10)
                                .using(LangChain4jTokenizerDefinition.TokenizerType.OPEN_AI)
                                .end());
            }
        };
    }

    /** A body that no converter knows. */
    private static final class Order {
    }
}
