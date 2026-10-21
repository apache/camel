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
package org.apache.camel.processor.enricher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileDefaultBinding;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A pollEnrich that times out sets no body, also in a route that consumes files: the message of a file consumer creates
 * its body from the file on demand, which brought the file of the exchange back as the body, or as the variable of
 * variableReceive. The messages of JMS, mail and HTTP consumers create their body on demand as well.
 */
class PollEnrichTimeoutFileBodyTest extends ContextTestSupport {

    @TempDir
    Path directTempDir;

    @Test
    void theBodyIsNullWhenNothingWasPolled() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:body");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri("orders-body"), "ORD-1001", Exchange.FILE_NAME, "order-1001.json");

        mock.assertIsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getMessage().getBody(), "nothing was polled, so no body");
    }

    @Test
    void theVariableIsNullWhenNothingWasPolled() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:variable");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri("orders-variable"), "ORD-1001", Exchange.FILE_NAME, "order-1001.json");

        mock.assertIsSatisfied();
        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertNull(exchange.getVariable("customer"), "nothing was polled, so no variable");
        // the order is kept as the body
        assertEquals("ORD-1001", exchange.getMessage().getBody(String.class));
    }

    /**
     * Deterministic regression test for the race between GenericFileOnCompletion (which moves/deletes the file after
     * the exchange completes) and a downstream getBody(String.class) call on the restored variableReceive body.
     * <p>
     * Rather than relying on thread scheduling to win the race, this test uses a direct: route with a GenericFile body
     * and an OnCompletion that deletes the file synchronously before the assertion — guaranteeing the file is gone
     * before getBody(String.class) is called, so the test fails without the fix and passes with it.
     */
    @Test
    void theVariableBodySurvivesFileDeletionAfterCompletion() throws Exception {
        // Write the file that will serve as the exchange body
        Path orderFile = directTempDir.resolve("order-deterministic.json");
        Files.writeString(orderFile, "ORD-DET-001");

        // Build a GenericFile pointing to that file, as a file consumer would set on the exchange.
        // The binding holds the loaded body; a real file consumer populates it via loadContent.
        // We pre-load the content here so that the GenericFile is fully initialised and the
        // conversion to byte[] in PollEnricher can materialise it while the file still exists.
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(orderFile.toFile());
        genericFile.setFileName("order-deterministic.json");
        genericFile.setAbsoluteFilePath(orderFile.toString());
        genericFile.setEndpointPath(directTempDir.toString());
        GenericFileDefaultBinding<File> binding = new GenericFileDefaultBinding<>();
        binding.setBody(genericFile, orderFile.toFile());
        genericFile.setBinding(binding);

        MockEndpoint mock = getMockEndpoint("mock:variable-direct");
        mock.expectedMessageCount(1);

        template.send("direct:variable-direct", e -> {
            e.getIn().setBody(genericFile);
            // Register an OnCompletion that deletes the file synchronously, simulating GenericFileOnCompletion.
            // This fires after the exchange is done but before the test reads the body — the worst-case timing
            // that caused the flaky NoSuchFileException without the fix in PollEnricher.
            e.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onComplete(Exchange exchange) {
                    orderFile.toFile().delete();
                }
            });
        });

        mock.assertIsSatisfied();
        Exchange exchange = mock.getReceivedExchanges().get(0);

        assertNull(exchange.getVariable("customer"), "nothing was polled, so no variable");
        // File is now deleted — getBody(String.class) must still return the content
        assertThat(orderFile).doesNotExist();
        assertThat(exchange.getMessage().getBody(String.class))
                .as("original body must survive file deletion after completion")
                .isEqualTo("ORD-DET-001");
    }

    @Test
    void theBodyOfAMessageThatCreatesItOnDemandIsNull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:demand");
        mock.expectedMessageCount(1);

        template.send("direct:demand", e -> e.setIn(new OnDemandMessage(e)));

        mock.assertIsSatisfied();
        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertNull(exchange.getMessage().getBody(), "nothing was polled, so no body");
        assertEquals("ORD-1001", exchange.getMessage().getHeader("orderId"), "the headers are kept");
    }

    // creates its body on demand, as the message of a JMS or mail consumer does
    private static class OnDemandMessage extends DefaultMessage {

        OnDemandMessage(Exchange exchange) {
            super(exchange);
            setHeader("orderId", "ORD-1001");
        }

        @Override
        protected Object createBody() {
            return "the payload of the consumer";
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("orders-body?initialDelay=0&delay=10"))
                        .pollEnrich().simple(fileUri("customers?fileName=C-482.json")).timeout(100)
                        .to("mock:body");

                from(fileUri("orders-variable?initialDelay=0&delay=10"))
                        .pollEnrich().simple(fileUri("customers?fileName=C-482.json")).timeout(100)
                        .variableReceive("customer")
                        .to("mock:variable");

                from("direct:demand")
                        .pollEnrich().simple(fileUri("customers?fileName=C-482.json")).timeout(100)
                        .to("mock:demand");

                from("direct:variable-direct")
                        .pollEnrich().simple(fileUri("customers?fileName=C-482.json")).timeout(100)
                        .variableReceive("customer")
                        .to("mock:variable-direct");
            }
        };
    }
}
