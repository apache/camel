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
package org.apache.camel.component.langchain4j.ingest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jIngestValidationTest {

    @Test
    void zeroSegmentSizeFailsTheStart() throws Exception {
        assertBoundsRejected("langchain4j-ingest:pipe?maxSegmentSize=0");
    }

    @Test
    void zeroEmbeddingBatchSizeFailsTheStart() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:in").to("langchain4j-ingest:pipe?embeddingBatchSize=0");
                }
            });

            assertThatThrownBy(context::start)
                    .hasStackTraceContaining("embeddingBatchSize must be positive");
        }
    }

    @Test
    void blankPipelineNameFailsTheStart() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // a truly empty path is rejected by the URI parser already; a whitespace
                    // name (a blank property placeholder, say) reaches the endpoint
                    from("direct:in").to("langchain4j-ingest:%20");
                }
            });

            assertThatThrownBy(context::start)
                    .hasStackTraceContaining("pipeline name is missing");
        }
    }

    @Test
    void blankDocumentIdHeaderFailsTheStart() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:in").to("langchain4j-ingest:pipe?documentIdHeader=");
                }
            });

            assertThatThrownBy(context::start)
                    .hasStackTraceContaining("documentIdHeader must not be blank");
        }
    }

    @Test
    void negativeMaxDocumentSizeFailsTheStart() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:in").to("langchain4j-ingest:pipe?maxDocumentSize=-1");
                }
            });

            assertThatThrownBy(context::start)
                    .hasStackTraceContaining("maxDocumentSize must not be negative");
        }
    }

    @Test
    void overlapNotSmallerThanSegmentSizeFailsTheStart() throws Exception {
        assertBoundsRejected("langchain4j-ingest:pipe?maxSegmentSize=100&maxOverlapSize=100");
    }

    private static void assertBoundsRejected(String uri) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:in").to(uri);
                }
            });

            // the bounds are checked before bean resolution, so no store or model beans are needed
            assertThatThrownBy(context::start)
                    .hasStackTraceContaining("maxSegmentSize must be positive")
                    .hasStackTraceContaining("smaller than it");
        }
    }
}
