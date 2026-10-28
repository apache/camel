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
package org.apache.camel.component.docling.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docling.DoclingHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the docling consumer against a real docling-serve: an async conversion submitted by the producer
 * is picked up and emitted by the consumer when it completes.
 * <p>
 * The failure path (a conversion that completes exceptionally) is covered deterministically by the unit test
 * {@code DoclingConsumerFailureTest}; here we exercise the real end-to-end success path.
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "Too much resources on GitHub Actions")
class DoclingConsumerIT extends DoclingITestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:submitAsync")
                        .to("docling:convert?operation=SUBMIT_ASYNC_CONVERSION");
                from("docling:onComplete?initialDelay=0&delay=1000")
                        .to("mock:result");
            }
        };
    }

    @Test
    void consumesACompletedAsyncConversion() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMinimumMessageCount(1);
        result.setResultWaitTime(TimeUnit.SECONDS.toMillis(90));

        Path testFile = createTestFile();
        String taskId = template.requestBodyAndHeader("direct:submitAsync", testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);
        assertThat(taskId).isNotBlank();

        result.assertIsSatisfied();
        String body = result.getExchanges().get(0).getMessage().getBody(String.class);
        assertThat(body).containsIgnoringCase("Test Document");
    }

    private Path createTestFile() throws Exception {
        Path tempFile = Files.createTempFile("docling-consumer-test", ".md");
        Files.writeString(tempFile,
                """
                        # Test Document

                        This is a test document for Docling-Serve async consumer processing.

                        ## Section 1

                        Some content here.
                        """);
        return tempFile;
    }
}
