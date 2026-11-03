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
package org.apache.camel.component.mtconnect;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The producer's probe, current and sample requests, sent from a producer template. */
class MTConnectProducerTest extends MTConnectTestSupport {

    @Test
    void probeReturnsTheAgentDeviceModelDocumentScopedByTheDevice() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);

        Exchange reply = send("mtconnect:" + agent.url() + "?device=Trial&operation=probe", Map.of());

        String body = reply.getMessage().getBody(String.class);
        assertThat(body).startsWith("<?xml").contains("<MTConnectDevices", "name=\"Trial\"", "id=\"x_load\"")
                .doesNotContain("<Agent ");
        assertThat(reply.getMessage().getHeader(MTConnectConstants.INSTANCE_ID)).isEqualTo(agent.instanceId());
        assertThat(agent.requests()).containsExactly("/Trial/probe");
    }

    @Test
    void currentRepliesWithTheFieldsTheConsumerMessagesCarry() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.set("x_load", "11.5");
        agent.set("temp", "21.5", Map.of("statistic", "AVERAGE", "duration", "60"));
        agent.condition("sys_cond", "Fault", "E42", null, "HIGH", "Spindle overheat");
        ReceivedMessages consumed = consume("mtconnect:" + agent.url() + "?device=Trial&" + FAST);
        awaitAgent().untilAsserted(() -> assertThat(consumed.size()).isEqualTo(11));

        Exchange reply = send("mtconnect:" + agent.url() + "?device=Trial&operation=current", Map.of());

        List<Map<String, Object>> observations = observations(reply);
        assertThat(observations).hasSize(11);
        for (ReceivedMessages.Received message : consumed.all()) {
            Map<String, Object> expected = new HashMap<>();
            message.headers().forEach((name, value) -> expected.put(name.substring("CamelMTConnect".length()), value));
            expected.put("value", message.body());
            assertThat(observations).as(message.dataItemId()).contains(expected);
        }
        assertThat(observations).filteredOn(o -> "x_load".equals(o.get("DataItemId"))).singleElement()
                .satisfies(o -> assertThat(o).containsEntry("value", new BigDecimal("11.5"))
                        .containsEntry("Units", "PERCENT").containsEntry("Document", "Current"));
        assertThat(observations).filteredOn(o -> "temp".equals(o.get("DataItemId"))).singleElement()
                .satisfies(o -> assertThat(o).containsEntry("Statistic", "AVERAGE")
                        .containsEntry("Duration", new BigDecimal("60")));
        assertThat(reply.getMessage().getHeaders())
                .containsEntry(MTConnectConstants.INSTANCE_ID, agent.instanceId())
                .containsEntry(MTConnectConstants.NEXT_SEQUENCE, agent.nextSequence())
                .containsEntry(MTConnectConstants.LAST_SEQUENCE, agent.nextSequence() - 1)
                .containsEntry(MTConnectConstants.FIRST_SEQUENCE, 1L);
    }

    @Test
    void currentAtASequenceReturnsTheValuesTheAgentHeldThen() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        long first = agent.set("x_load", "1");
        agent.set("x_load", "2");

        Exchange reply = send("mtconnect:" + agent.url() + "?device=Trial&operation=current&at=" + first, Map.of());

        assertThat(observations(reply)).filteredOn(o -> "x_load".equals(o.get("DataItemId"))).singleElement()
                .satisfies(o -> assertThat(o).containsEntry("value", BigDecimal.ONE).containsEntry("Sequence", first));
        assertThat(reply.getMessage().getHeader(MTConnectConstants.NEXT_SEQUENCE)).isEqualTo(agent.nextSequence());
    }

    @Test
    void sampleReturnsARangeAndTheNextSequenceToPageOn() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        long first = agent.set("x_load", "1");
        long second = agent.set("x_load", "2");
        long third = agent.set("x_load", "3");
        agent.set("x_load", "4");
        String uri = "mtconnect:" + agent.url() + "?device=Trial&operation=sample&from=" + first + "&to=" + third;

        Exchange range = send(uri, Map.of());
        Exchange page = send(uri + "&count=2", Map.of());

        assertThat(observations(range)).extracting(o -> o.get("Sequence")).containsExactly(first, second, third);
        assertThat(observations(range)).allSatisfy(o -> assertThat(o).containsEntry("Document", "Sample"));
        assertThat(observations(page)).extracting(o -> o.get("value"))
                .containsExactly(BigDecimal.ONE, new BigDecimal("2"));
        assertThat(page.getMessage().getHeader(MTConnectConstants.NEXT_SEQUENCE)).isEqualTo(third);
    }

    @Test
    void headersOverrideTheEndpointOptionsForOneMessage() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        long first = agent.set("x_load", "1");
        long second = agent.set("x_load", "2");
        String uri = "mtconnect:" + agent.url() + "?device=Trial&operation=probe&from=1&to=1&count=1&at=1";

        Exchange sample = send(uri, Map.of(MTConnectConstants.OPERATION, "sample", MTConnectConstants.FROM, first,
                MTConnectConstants.TO, second, MTConnectConstants.COUNT, 5));
        Exchange current = send(uri, Map.of(MTConnectConstants.OPERATION, "current", MTConnectConstants.AT, first));

        assertThat(observations(sample)).extracting(o -> o.get("Sequence")).containsExactly(first, second);
        assertThat(observations(current)).filteredOn(o -> "x_load".equals(o.get("DataItemId")))
                .extracting(o -> o.get("Sequence")).containsExactly(first);
    }

    @Test
    void anExchangeWithoutAnOperationFailsNamingTheOptionAndTheHeader() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);

        Exchange reply = send("mtconnect:" + agent.url() + "?device=Trial", Map.of());

        assertThat(reply.getException()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation option").hasMessageContaining(MTConnectConstants.OPERATION);
        assertThat(agent.requests()).isEmpty();
    }

    @Test
    void anUnknownOperationInTheHeaderFailsTheExchangeNamingTheValue() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);

        Exchange reply = send("mtconnect:" + agent.url() + "?device=Trial&operation=probe",
                Map.of(MTConnectConstants.OPERATION, "read"));

        assertThat(reply.getException()).hasMessageContaining("read");
        assertThat(agent.requests()).isEmpty();
    }

    @ParameterizedTest(name = "{0} answers OUT_OF_RANGE with {1}")
    @CsvSource({ "V2_5, 400, Trial, x_load", "V1_3, 200, MACHINE-01, xl" })
    void anAgentErrorFailsTheExchangeWithTheAgentCodesAndTexts(
            StubDialect dialect, int status, String device, String item)
            throws Exception {
        assertThat(dialect.outOfRangeStatus).isEqualTo(status);
        StubAgent agent = agent(dialect);
        long overwritten = agent.set(item, "1");
        agent.overrun(item);

        Exchange reply = send("mtconnect:" + agent.url() + "?device=" + device + "&operation=sample&from=" + overwritten,
                Map.of());

        assertThat(reply.getException()).isInstanceOfSatisfying(MTConnectErrorException.class, e -> {
            assertThat(e.getErrorCodes()).containsExactly("OUT_OF_RANGE");
            assertThat(e.getErrorTexts()).singleElement().asString().startsWith("'from' must be greater than");
            assertThat(e.hasErrorCode("OUT_OF_RANGE")).isTrue();
            assertThat(e.getAgentUrl()).isEqualTo(agent.url());
            assertThat(e).hasMessageContaining("OUT_OF_RANGE: 'from' must be greater than");
        });
    }

    @Test
    void anUnknownDeviceFailsTheExchangeWithNoDevice() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);

        Exchange reply = send("mtconnect:" + agent.url() + "?device=Lathe&operation=probe", Map.of());

        assertThat(reply.getException()).isInstanceOfSatisfying(MTConnectErrorException.class,
                e -> assertThat(e.getErrorCodes()).containsExactly("NO_DEVICE"));
    }

    @Test
    void anUnreachableAgentFailsTheExchangeWithTheTransportCause() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.refuse();

        Exchange reply = send("mtconnect:" + agent.url() + "?device=Trial&operation=probe", Map.of());

        assertThat(reply.getException()).isInstanceOf(CamelExchangeException.class)
                .hasMessageContaining("cannot be read").hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    void anHttpErrorWithoutAnErrorDocumentFailsTheExchangeWithTheStatus() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.answer(StubAgent.Request.PROBE, StubAgent.Answer.html(502));

        Exchange reply = send("mtconnect:" + agent.url() + "?device=Trial&operation=probe", Map.of());

        assertThat(reply.getException()).isInstanceOf(CamelExchangeException.class).hasMessageContaining("HTTP 502");
    }

    @Test
    void aReplyFromARestartedAgentIsMappedWithTheProbeReadAgain() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        String uri = "mtconnect:" + agent.url() + "?device=Trial&operation=current";
        send(uri, Map.of());
        agent.restart();
        agent.set("x_load", "5");

        Exchange reply = send(uri, Map.of());

        assertThat(observations(reply)).filteredOn(o -> "x_load".equals(o.get("DataItemId"))).singleElement()
                .satisfies(o -> assertThat(o).containsEntry("InstanceId", agent.instanceId())
                        .containsEntry("Units", "PERCENT"));
        assertThat(agent.requests()).filteredOn(request -> request.endsWith("/probe")).hasSize(2);
    }

    @Test
    void theSampleCountIsLoweredToTheAgentBufferSizeAndThePathReachesTheAgent() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.bufferSize(64);

        Exchange reply = send("mtconnect:" + agent.url() + "?device=Trial&operation=sample&path=RAW(//Axes)", Map.of());

        assertThat(reply.getException()).isNull();
        assertThat(agent.requests()).filteredOn(request -> request.contains("/sample"))
                .containsExactly("/Trial/sample?count=64&path=%2F%2FAxes");
    }

    private Exchange send(String uri, Map<String, Object> headers) {
        return template.send(uri, exchange -> exchange.getMessage().setHeaders(new HashMap<>(headers)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> observations(Exchange reply) {
        assertThat(reply.getException()).isNull();
        return reply.getMessage().getBody(List.class);
    }
}
