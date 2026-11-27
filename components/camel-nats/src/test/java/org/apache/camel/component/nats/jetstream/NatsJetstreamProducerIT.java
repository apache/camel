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
package org.apache.camel.component.nats.jetstream;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.api.StreamConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.component.nats.NatsComponent;
import org.apache.camel.component.nats.integration.NatsITSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JetStream producer against a NATS server (CAMEL-27198): the requestTimeout reaches jnats, a missing
 * acknowledgement is reported clearly, and the stream of jetstreamName is created when it does not exist.
 */
@Isolated
public class NatsJetstreamProducerIT extends NatsITSupport {

    private Connection admin;
    private JetStreamManagement jsm;

    @BeforeEach
    void connect() throws Exception {
        admin = Nats.connect(context.getComponent("nats", NatsComponent.class).getConfiguration().getServers());
        jsm = admin.jetStreamManagement();
    }

    @AfterEach
    void close() throws Exception {
        for (String name : jsm.getStreamNames()) {
            if (name.startsWith("CAMEL27198")) {
                jsm.deleteStream(name);
            }
        }
        admin.close();
    }

    /**
     * A JetStream publish to a subject that no stream takes in, but that a core NATS subscriber listens to, gets no
     * acknowledgement (and no 503 No Responders either).
     */
    private void silentSubscriber(String subject) throws Exception {
        Dispatcher d = admin.createDispatcher(msg -> {
            // never answers
        });
        d.subscribe(subject);
        admin.flush(Duration.ofSeconds(5));
    }

    private long sendAndTimeMillis(String uri, Exchange[] result) {
        long start = System.nanoTime();
        result[0] = template.send(uri, e -> e.getIn().setBody("Hello"));
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    @Test
    void synchronousPublishWaitsRequestTimeout() throws Exception {
        silentSubscriber("camel27198.sync");
        Exchange[] result = new Exchange[1];

        // longer than the connection timeout (2000 ms) that jnats used before
        long millis = sendAndTimeMillis("nats:camel27198.sync?jetstreamEnabled=true&jetstreamAsync=false&requestTimeout=3000",
                result);

        assertNotNull(result[0].getException());
        assertTrue(millis >= 2900, "waited " + millis + " ms");
        assertTrue(millis < 10000, "waited " + millis + " ms");

        // and shorter
        millis = sendAndTimeMillis("nats:camel27198.sync?jetstreamEnabled=true&jetstreamAsync=false&requestTimeout=500",
                result);

        assertNotNull(result[0].getException());
        assertTrue(millis < 1500, "waited " + millis + " ms");
    }

    @Test
    void asynchronousTimeoutSaysWhatHappened() throws Exception {
        silentSubscriber("camel27198.async");
        Exchange[] result = new Exchange[1];

        long millis = sendAndTimeMillis("nats:camel27198.async?jetstreamEnabled=true&requestTimeout=500", result);

        Exception e = result[0].getException();
        assertNotNull(e);
        assertEquals("No acknowledgement from JetStream for subject camel27198.async within 500 ms", e.getMessage());
        assertTrue(millis < 1500, "waited " + millis + " ms");
    }

    @Test
    void asynchronousCancelSaysWhatHappened() throws Exception {
        silentSubscriber("camel27198.cancel");
        Exchange[] result = new Exchange[1];

        // jnats gives up on the acknowledgement after requestCleanupInterval (to twice that), before requestTimeout
        long millis = sendAndTimeMillis(
                "nats:camel27198.cancel?jetstreamEnabled=true&requestTimeout=20000&requestCleanupInterval=1000", result);

        Exception e = result[0].getException();
        assertNotNull(e);
        assertTrue(e.getMessage().startsWith("No acknowledgement from JetStream for subject camel27198.cancel: "),
                e.getMessage());
        assertTrue(e.getMessage().contains("requestCleanupInterval, 1000 ms"), e.getMessage());
        assertTrue(millis < 10000, "waited " + millis + " ms");
    }

    @Test
    void producerCreatesTheStreamOfJetstreamName() throws Exception {
        Exchange exchange = template.send("nats:camel27198.created?jetstreamEnabled=true&jetstreamName=CAMEL27198_CREATED",
                e -> e.getIn().setBody("Hello"));

        assertNull(exchange.getException());
        assertEquals(1, jsm.getStreamInfo("CAMEL27198_CREATED").getStreamState().getMsgCount());
        assertEquals(List.of("camel27198.created"),
                jsm.getStreamInfo("CAMEL27198_CREATED").getConfiguration().getSubjects());
    }

    @Test
    void producerDoesNotCreateAStreamWhenAnotherTakesInTheSubject() throws Exception {
        jsm.addStream(StreamConfiguration.builder().name("CAMEL27198_OTHER").subjects("camel27198.other").build());

        Exchange exchange = template.send("nats:camel27198.other?jetstreamEnabled=true&jetstreamName=CAMEL27198_MISSING",
                e -> e.getIn().setBody("Hello"));

        assertNull(exchange.getException());
        assertEquals(1, jsm.getStreamInfo("CAMEL27198_OTHER").getStreamState().getMsgCount());
        JetStreamApiException e
                = assertThrows(JetStreamApiException.class, () -> jsm.getStreamInfo("CAMEL27198_MISSING"));
        assertEquals(404, e.getErrorCode());
    }
}
