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
package org.apache.camel.component.docker.consumer;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.Statistics;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docker.DockerComponent;
import org.apache.camel.component.docker.DockerConfiguration;
import org.apache.camel.component.docker.util.DockerTestUtils;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The events and stats consumers read a stream that docker-java opens on its own thread. The stream belongs to the
 * callback given to {@code exec}: closing the command does not close it.
 */
class DockerEventsConsumerStreamTest extends CamelTestSupport {

    private final DockerClient dockerClient = mock(DockerClient.class);
    private final List<ResultCallback<Event>> eventCallbacks = new CopyOnWriteArrayList<>();
    private final List<ResultCallback<Statistics>> statsCallbacks = new CopyOnWriteArrayList<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        DockerConfiguration configuration = new DockerConfiguration();
        configuration.setParameters(DockerTestUtils.getDefaultParameters("localhost", 2375, configuration));
        DockerComponent component = new DockerComponent(configuration);
        component.setClient(DockerTestUtils.getClientProfile("localhost", 2375, configuration), dockerClient);
        camelContext.addComponent("docker", component);

        when(dockerClient.eventsCmd()).thenAnswer(i -> {
            EventsCmd cmd = mock(EventsCmd.class);
            when(cmd.withSince(anyString())).thenReturn(cmd);
            when(cmd.exec(any())).thenAnswer(e -> {
                ResultCallback<Event> callback = e.getArgument(0);
                eventCallbacks.add(callback);
                return callback;
            });
            return cmd;
        });
        when(dockerClient.statsCmd(anyString())).thenAnswer(i -> {
            StatsCmd cmd = mock(StatsCmd.class);
            when(cmd.exec(any())).thenAnswer(e -> {
                ResultCallback<Statistics> callback = e.getArgument(0);
                statsCallbacks.add(callback);
                return callback;
            });
            return cmd;
        });
        return camelContext;
    }

    @Test
    void stopClosesTheEventStreamAndARestartReceivesEachEventOnce() throws Exception {
        addRoute("docker://events?host=localhost&port=2375");
        AtomicInteger closed = new AtomicInteger();
        eventCallbacks.get(0).onStart(closed::incrementAndGet);

        context.getRouteController().stopRoute("docker");
        context.getRouteController().startRoute("docker");
        assertEquals(2, eventCallbacks.size());
        eventCallbacks.get(1).onStart(() -> {
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        Event event = new Event("start", "c1", "image", 1L);
        // the daemon sends the event to every stream that is still open
        for (ResultCallback<Event> callback : eventCallbacks) {
            callback.onNext(event);
        }
        mock.assertIsSatisfied();
        assertEquals(1, closed.get(), "stopping the route did not close the event stream");
    }

    @Test
    void stopBeforeTheStreamIsOpenClosesItWhenItOpens() throws Exception {
        addRoute("docker://events?host=localhost&port=2375");
        context.getRouteController().stopRoute("docker");

        AtomicInteger closed = new AtomicInteger();
        eventCallbacks.get(0).onStart(closed::incrementAndGet);
        assertEquals(1, closed.get(), "the stream opened after the stop was not closed");
    }

    @Test
    void eventStreamThatFailsIsOpenedAgain() throws Exception {
        addRoute("docker://events?host=localhost&port=2375");
        eventCallbacks.get(0).onStart(() -> {
        });

        // the connection to the daemon is lost (for example the daemon restarts)
        eventCallbacks.get(0).onError(new IOException("Connection reset by peer"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> eventCallbacks.size() == 2);

        // the daemon is not back yet: the new stream fails before it is open, and is tried again later
        eventCallbacks.get(1).onError(new ConnectException("Connection refused"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> eventCallbacks.size() == 3);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(new Event("start", "c2", "image", 2L));
        eventCallbacks.get(2).onStart(() -> {
        });
        eventCallbacks.get(2).onNext(new Event("start", "c2", "image", 2L));
        mock.assertIsSatisfied();
    }

    @Test
    void eventStreamThatEndsIsOpenedAgain() throws Exception {
        addRoute("docker://events?host=localhost&port=2375");
        eventCallbacks.get(0).onStart(() -> {
        });

        // the daemon ends the stream (for example when it shuts down)
        eventCallbacks.get(0).onComplete();
        await().atMost(10, TimeUnit.SECONDS).until(() -> eventCallbacks.size() == 2);
    }

    @Test
    void stopClosesTheStatsStream() throws Exception {
        addRoute("docker://stats?host=localhost&port=2375&containerId=470b9b823e6c");
        AtomicInteger closed = new AtomicInteger();
        Closeable stream = closed::incrementAndGet;
        statsCallbacks.get(0).onStart(stream);

        context.getRouteController().stopRoute("docker");
        assertEquals(1, closed.get(), "stopping the route did not close the stats stream");
    }

    private void addRoute(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("docker").to("mock:result");
            }
        });
        context.start();
    }
}
