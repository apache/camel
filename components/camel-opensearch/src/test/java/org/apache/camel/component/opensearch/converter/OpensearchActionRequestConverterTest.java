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
package org.apache.camel.component.opensearch.converter;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.opensearch.OpensearchConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpensearchActionRequestConverterTest {

    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private IndexRequest.Builder<Object> preBuiltIndexBuilder() {
        return new IndexRequest.Builder<>().index("idx").id("original").document(Map.of("k", "v"));
    }

    @Test
    void preBuiltIndexBuilderKeepsItsIdWhenHeaderAbsent() throws Exception {
        Exchange exchange = new DefaultExchange(context);

        IndexRequest.Builder<?> result
                = OpensearchActionRequestConverter.toIndexRequestBuilder(preBuiltIndexBuilder(), exchange);

        // no CamelOpensearchIndexId header -> the caller's id must be preserved, not overwritten with null
        assertEquals("original", result.build().id());
    }

    @Test
    void preBuiltIndexBuilderHeaderOverridesId() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(OpensearchConstants.PARAM_INDEX_ID, "fromHeader");

        IndexRequest.Builder<?> result
                = OpensearchActionRequestConverter.toIndexRequestBuilder(preBuiltIndexBuilder(), exchange);

        assertEquals("fromHeader", result.build().id());
    }

    @Test
    void preBuiltUpdateBuilderKeepsItsIdWhenHeaderAbsent() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        UpdateRequest.Builder<Object, Object> preBuilt
                = new UpdateRequest.Builder<>().index("idx").id("original").doc(Map.of("k", "v"));

        UpdateRequest.Builder<?, ?> result
                = OpensearchActionRequestConverter.toUpdateRequestBuilder(preBuilt, exchange);

        // no CamelOpensearchIndexId header -> the caller's id must be preserved, not overwritten with null
        assertEquals("original", result.build().id());
    }
}
