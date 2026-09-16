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
package org.apache.camel.component.opa;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transport underneath {@code evaluationMode=rest}.
 * <p/>
 * A server that refuses a connection fails fast and is already covered; what is not, and what an operator actually
 * meets, is a server that <em>accepts</em> and then says nothing. The SDK's own transport applies no timeout of any
 * kind, so that case parked the routing thread for ever - and a component that fails closed never reached the point of
 * denying, it simply stopped.
 */
public class OpaRestTransportTest extends CamelTestSupport {

    private final List<Closeable> open = new ArrayList<>();

    private interface Closeable extends AutoCloseable {
        @Override
        void close() throws IOException;
    }

    @AfterEach
    void closeSockets() throws Exception {
        for (Closeable c : open) {
            c.close();
        }
        open.clear();
    }

    /**
     * A listener that completes the TCP handshake and then never answers. Deliberately not an HttpServer: the point is
     * a peer that is reachable but mute, which is what a wedged OPA looks like from the client side.
     */
    private int mutePort() throws IOException {
        ServerSocket listener = new ServerSocket(0);
        open.add(listener::close);
        Thread accepter = new Thread(() -> {
            try {
                listener.accept();
                // hold it: no read, no write, no close. Ends when the test closes the listener.
            } catch (IOException e) {
                // the listener was closed as the test finished
            }
        }, "opa-mute-server");
        accepter.setDaemon(true);
        accepter.start();
        return listener.getLocalPort();
    }

    @Test
    @Timeout(60)
    void failsClosedWhenTheServerAcceptsAndThenSaysNothing() throws Exception {
        Exchange out = template.request(
                "opa:authz/allow?serverUrl=http://localhost:" + mutePort() + "&requestTimeout=500", e -> {
                });

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }

    @Test
    @Timeout(60)
    void proceedsOnTheSameStallWhenFailOpenIsSet() throws Exception {
        // a timeout is a failure to reach a verdict, not a deny, so it is handled like every other such failure
        Exchange out = template.request(
                "opa:authz/allow?serverUrl=http://localhost:" + mutePort() + "&requestTimeout=500&failOpen=true",
                e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }
}
