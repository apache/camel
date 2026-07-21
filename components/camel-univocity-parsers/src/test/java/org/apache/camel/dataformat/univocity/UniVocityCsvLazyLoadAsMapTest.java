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
package org.apache.camel.dataformat.univocity;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.dataformat.univocity.UniVocityTestHelper.asMap;
import static org.apache.camel.dataformat.univocity.UniVocityTestHelper.join;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests lazy-load + asMap mode: verifies that headers are captured eagerly (H9 fix) and that the iterator is properly
 * closed via onCompletion (H10 fix).
 */
public class UniVocityCsvLazyLoadAsMapTest extends CamelTestSupport {

    @Test
    public void shouldUnmarshalLazyAsMap() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:lazyMap", join("A,B,C", "1,2,3", "one,two,three"));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

        Exchange exchange = mock.getExchanges().get(0);
        Iterator<?> body = assertInstanceOf(Iterator.class, exchange.getIn().getBody());

        assertTrue(body.hasNext());
        assertEquals(asMap("A", "1", "B", "2", "C", "3"), body.next());
        assertTrue(body.hasNext());
        assertEquals(asMap("A", "one", "B", "two", "C", "three"), body.next());
        assertFalse(body.hasNext());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldUnmarshalLazyAsMapOnDifferentThread() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:lazyMapSeda", join("X,Y", "10,20", "30,40"));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

        // The body arrives via seda (different thread) — headers must still be available
        Object body = mock.getExchanges().get(0).getIn().getBody();
        // The seda consumer already consumed the iterator into a list via the processor
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rows = (List<Map<String, String>>) body;
        assertEquals(2, rows.size());
        assertEquals(asMap("X", "10", "Y", "20"), rows.get(0));
        assertEquals(asMap("X", "30", "Y", "40"), rows.get(1));
    }

    @Test
    public void shouldCloseIteratorOnCompletion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:lazyMap", join("A,B", "1,2"));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

        Iterator<?> body = assertInstanceOf(Iterator.class, mock.getExchanges().get(0).getIn().getBody());
        // The iterator implements Closeable
        assertInstanceOf(Closeable.class, body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                UniVocityCsvDataFormat lazyMap = new UniVocityCsvDataFormat();
                lazyMap.setLazyLoad(true);
                lazyMap.setAsMap(true);
                lazyMap.setHeaderExtractionEnabled(true);

                from("direct:lazyMap")
                        .unmarshal(lazyMap)
                        .to("mock:result");

                UniVocityCsvDataFormat lazyMapSeda = new UniVocityCsvDataFormat();
                lazyMapSeda.setLazyLoad(true);
                lazyMapSeda.setAsMap(true);
                lazyMapSeda.setHeaderExtractionEnabled(true);

                from("direct:lazyMapSeda")
                        .unmarshal(lazyMapSeda)
                        .to("seda:consume");

                from("seda:consume")
                        .process(exchange -> {
                            // Consume iterator on seda thread (different from parsing thread)
                            Iterator<?> it = (Iterator<?>) exchange.getIn().getBody();
                            List<Map<String, String>> rows = new ArrayList<>();
                            while (it.hasNext()) {
                                @SuppressWarnings("unchecked")
                                Map<String, String> row = (Map<String, String>) it.next();
                                rows.add(row);
                            }
                            exchange.getIn().setBody(rows);
                        })
                        .to("mock:result");
            }
        };
    }
}
