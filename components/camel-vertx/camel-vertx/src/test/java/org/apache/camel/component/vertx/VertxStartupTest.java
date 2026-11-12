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
package org.apache.camel.component.vertx;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.transport.Transport;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The component waits for the clustered EventBus before it reports itself started, so a build that never finishes or
 * that fails has to fail the start rather than leave the component without a Vert.x instance.
 */
public class VertxStartupTest {

    @Test
    public void shouldFailWhenTheEventBusIsNotReadyWithinTheTimeout() {
        // a build that never completes, so the component waits out its timeout
        VertxComponent component = clusteredComponent(Promise.<Vertx> promise().future());
        component.setTimeout(1);

        Exception e = assertThrows(Exception.class, component::start);

        assertTrue(messages(e).contains("not ready within the timeout"), messages(e));
        assertNull(component.getVertx(), "the component must not be left holding a half-built Vert.x");
    }

    @Test
    public void shouldFailWhenBuildingTheClusteredVertxFails() {
        VertxComponent component
                = clusteredComponent(Future.failedFuture(new IllegalStateException("no cluster manager on the classpath")));

        Exception e = assertThrows(Exception.class, component::start);

        assertTrue(messages(e).contains("Error creating Clustered Vertx"), messages(e));
        assertTrue(messages(e).contains("no cluster manager on the classpath"), messages(e));
        assertNull(component.getVertx(), "the component must not be left holding a half-built Vert.x");
    }

    private static VertxComponent clusteredComponent(Future<Vertx> clustered) {
        VertxComponent component = new VertxComponent();
        component.setCamelContext(new DefaultCamelContext());
        // a host makes the component build a clustered Vert.x, which is the asynchronous path
        component.setHost("localhost");
        component.setVertxFactory(new StubVertxBuilder(clustered));
        return component;
    }

    private static String messages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable current = t; current != null; current = current.getCause()) {
            sb.append(current).append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * A builder whose clustered build is whatever the test hands it.
     */
    private static final class StubVertxBuilder implements VertxBuilder {

        private final Future<Vertx> clustered;

        private StubVertxBuilder(Future<Vertx> clustered) {
            this.clustered = clustered;
        }

        @Override
        public VertxBuilder with(VertxOptions options) {
            return this;
        }

        @Override
        public VertxBuilder withMetrics(VertxMetricsFactory factory) {
            return this;
        }

        @Override
        public VertxBuilder withTracer(VertxTracerFactory factory) {
            return this;
        }

        @Override
        public VertxBuilder withTransport(Transport transport) {
            return this;
        }

        @Override
        public VertxBuilder withClusterManager(ClusterManager clusterManager) {
            return this;
        }

        @Override
        public Vertx build() {
            throw new UnsupportedOperationException("this test only builds a clustered Vert.x");
        }

        @Override
        public Future<Vertx> buildClustered() {
            return clustered;
        }
    }

}
