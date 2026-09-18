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
package org.apache.camel.component.jms.reply;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.DefaultJmsMessageListenerContainer;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.jms.TemporaryQueueResolver;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemporaryQueueReplyManagerRefreshTest {

    @RegisterExtension
    static DefaultCamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createVMService();

    private TemporaryQueueReplyManager replyManager;
    private JmsEndpoint startedEndpoint;

    @BeforeEach
    void setUp() {
        CamelContext context = contextExtension.getContext();
        JmsEndpoint endpoint = mock(JmsEndpoint.class);
        when(endpoint.getCamelContext()).thenReturn(context);
        when(endpoint.getConfiguration()).thenReturn(new JmsConfiguration());
        when(endpoint.getDestinationName()).thenReturn("TemporaryQueueReplyManagerRefreshTest");
        when(endpoint.getEndpointUri()).thenReturn("jms:queue:TemporaryQueueReplyManagerRefreshTest");

        replyManager = new TemporaryQueueReplyManager(context, null);
        replyManager.setEndpoint(endpoint);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (replyManager != null && replyManager.isStarted()) {
            replyManager.stop();
        }
        if (startedEndpoint != null && startedEndpoint.isStarted()) {
            startedEndpoint.stop();
        }
    }

    @Test
    void shouldRecoverReplyDestinationAfterRefreshWithRunningListenerContainer() throws Exception {
        CamelContext context = contextExtension.getContext();
        JmsComponent component = jmsComponentAutoAcknowledge(ConnectionFactoryHelper.createConnectionFactory(service));
        context.addComponent("jms", component);

        startedEndpoint = (JmsEndpoint) component.createEndpoint(
                "jms:queue:TemporaryQueueReplyManagerRefreshTest?recoveryInterval=100");
        ServiceHelper.startService(startedEndpoint);

        replyManager = new TemporaryQueueReplyManager(context, null);
        replyManager.setEndpoint(startedEndpoint);

        ScheduledExecutorService scheduledExecutorService
                = context.getExecutorServiceManager().newSingleThreadScheduledExecutor(replyManager, "test-timeout-checker");
        ExecutorService onTimeoutExecutorService
                = context.getExecutorServiceManager().newThreadPool(replyManager, "test-on-timeout", 0, 1);
        replyManager.setScheduledExecutorService(scheduledExecutorService);
        replyManager.setOnTimeoutExecutorService(onTimeoutExecutorService);
        replyManager.start();

        assertThat(replyManager.listenerContainer).isInstanceOf(DefaultJmsMessageListenerContainer.class);
        DefaultJmsMessageListenerContainer listenerContainer
                = (DefaultJmsMessageListenerContainer) replyManager.listenerContainer;
        assertThat(listenerContainer.isRunning()).isTrue();

        TemporaryQueueReplyManager.TemporaryReplyQueueDestinationResolver destinationResolver
                = replyManager.destinationResolver;

        Destination firstReplyTo = replyManager.getReplyTo();
        assertThat(firstReplyTo).isNotNull();
        assertThat(destinationResolver.isRefreshPending()).isFalse();

        destinationResolver.scheduleRefresh();
        assertThat(destinationResolver.isRefreshPending()).isTrue();

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(destinationResolver.isRefreshPending()).isFalse();
            assertThat(replyManager.getReplyTo()).isNotNull();
            assertThat(listenerContainer.isRunning()).isTrue();
        });

        assertThat(replyManager.getReplyTo()).isNotEqualTo(firstReplyTo);
    }

    @Test
    void shouldRetryAfterFailedTemporaryQueueCreation() throws Exception {
        FailOnSecondCreateResolver resolver = new FailOnSecondCreateResolver();
        replyManager = new TemporaryQueueReplyManager(contextExtension.getContext(), resolver);
        replyManager.setEndpoint(createEndpoint());

        try (Connection connection = ConnectionFactoryHelper.createConnectionFactory(service).createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            TemporaryQueueReplyManager.TemporaryReplyQueueDestinationResolver destinationResolver
                    = replyManager.destinationResolver;

            destinationResolver.resolveDestinationName(session, "temporary", false);
            assertThat(replyManager.getReplyTo()).isNotNull();
            assertThat(resolver.createAttempts.get()).isEqualTo(1);

            destinationResolver.scheduleRefresh();
            assertThat(destinationResolver.isRefreshPending()).isTrue();
            assertThat(replyManager.getReplyTo()).isNull();

            assertThatThrownBy(() -> destinationResolver.resolveDestinationName(session, "temporary", false))
                    .isInstanceOf(JMSException.class);
            assertThat(destinationResolver.isRefreshPending()).isTrue();
            assertThat(replyManager.getReplyTo()).isNull();
            assertThat(resolver.createAttempts.get()).isEqualTo(2);

            destinationResolver.resolveDestinationName(session, "temporary", false);
            assertThat(destinationResolver.isRefreshPending()).isFalse();
            assertThat(replyManager.getReplyTo()).isNotNull();
            assertThat(resolver.createAttempts.get()).isEqualTo(3);
        }
    }

    @Test
    void shouldPublishLatestRefreshGeneration() throws Exception {
        CountingTemporaryQueueResolver resolver = new CountingTemporaryQueueResolver();
        replyManager = new TemporaryQueueReplyManager(contextExtension.getContext(), resolver);
        replyManager.setEndpoint(createEndpoint());

        try (Connection connection = ConnectionFactoryHelper.createConnectionFactory(service).createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            TemporaryQueueReplyManager.TemporaryReplyQueueDestinationResolver destinationResolver
                    = replyManager.destinationResolver;

            TemporaryQueue firstQueue
                    = (TemporaryQueue) destinationResolver.resolveDestinationName(session, "temporary", false);
            assertThat(replyManager.getReplyTo()).isEqualTo(firstQueue);

            destinationResolver.scheduleRefresh();
            destinationResolver.scheduleRefresh();

            TemporaryQueue resolvedQueue
                    = (TemporaryQueue) destinationResolver.resolveDestinationName(session, "temporary", false);

            assertThat(destinationResolver.isRefreshPending()).isFalse();
            assertThat(replyManager.getReplyTo()).isEqualTo(resolvedQueue);
            assertThat(resolvedQueue).isNotEqualTo(firstQueue);
            assertThat(resolver.createAttempts.get()).isEqualTo(2);
        }
    }

    @Test
    void shouldKeepRefreshPendingWhenCreationKeepsFailing() throws Exception {
        AlwaysFailingTemporaryQueueResolver resolver = new AlwaysFailingTemporaryQueueResolver();
        replyManager = new TemporaryQueueReplyManager(contextExtension.getContext(), resolver);
        replyManager.setEndpoint(createEndpoint());

        try (Connection connection = ConnectionFactoryHelper.createConnectionFactory(service).createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            TemporaryQueueReplyManager.TemporaryReplyQueueDestinationResolver destinationResolver
                    = replyManager.destinationResolver;

            destinationResolver.scheduleRefresh();
            assertThatThrownBy(() -> destinationResolver.resolveDestinationName(session, "temporary", false))
                    .isInstanceOf(JMSException.class);

            assertThat(destinationResolver.isRefreshPending()).isTrue();
            assertThat(replyManager.getReplyTo()).isNull();
        }
    }

    @Test
    void shouldDiscardPublishWhenRefreshGenerationChangesDuringResolve() throws Exception {
        InterleavingRefreshResolver resolver = new InterleavingRefreshResolver();
        replyManager = new TemporaryQueueReplyManager(contextExtension.getContext(), resolver);
        replyManager.setEndpoint(createEndpoint());
        resolver.attach(replyManager.destinationResolver);

        try (Connection connection = ConnectionFactoryHelper.createConnectionFactory(service).createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            TemporaryQueueReplyManager.TemporaryReplyQueueDestinationResolver destinationResolver
                    = replyManager.destinationResolver;

            destinationResolver.resolveDestinationName(session, "temporary", false);
            assertThat(replyManager.getReplyTo()).isNotNull();

            destinationResolver.scheduleRefresh();
            assertThat(destinationResolver.resolveDestinationName(session, "temporary", false)).isNull();
            assertThat(destinationResolver.isRefreshPending()).isTrue();
            assertThat(replyManager.getReplyTo()).isNull();

            TemporaryQueue resolvedQueue
                    = (TemporaryQueue) destinationResolver.resolveDestinationName(session, "temporary", false);
            assertThat(resolvedQueue).isNotNull();
            assertThat(destinationResolver.isRefreshPending()).isFalse();
            assertThat(replyManager.getReplyTo()).isEqualTo(resolvedQueue);
        }
    }

    private JmsEndpoint createEndpoint() {
        JmsEndpoint endpoint = mock(JmsEndpoint.class);
        when(endpoint.getCamelContext()).thenReturn(contextExtension.getContext());
        when(endpoint.getConfiguration()).thenReturn(new JmsConfiguration());
        when(endpoint.getDestinationName()).thenReturn("TemporaryQueueReplyManagerRefreshTest");
        when(endpoint.getEndpointUri()).thenReturn("jms:queue:TemporaryQueueReplyManagerRefreshTest");
        return endpoint;
    }

    private static final class FailOnSecondCreateResolver implements TemporaryQueueResolver {
        private final AtomicInteger createAttempts = new AtomicInteger();

        @Override
        public TemporaryQueue createTemporaryQueue(Session session) throws JMSException {
            if (createAttempts.incrementAndGet() == 2) {
                throw new JMSException("simulated broker failure");
            }
            return session.createTemporaryQueue();
        }

        @Override
        public void delete(TemporaryQueue queue) {
            // noop
        }
    }

    private static final class CountingTemporaryQueueResolver implements TemporaryQueueResolver {
        private final AtomicInteger createAttempts = new AtomicInteger();

        @Override
        public TemporaryQueue createTemporaryQueue(Session session) throws JMSException {
            createAttempts.incrementAndGet();
            return session.createTemporaryQueue();
        }

        @Override
        public void delete(TemporaryQueue queue) {
            // noop
        }
    }

    private static final class AlwaysFailingTemporaryQueueResolver implements TemporaryQueueResolver {
        @Override
        public TemporaryQueue createTemporaryQueue(Session session) throws JMSException {
            throw new JMSException("simulated broker failure");
        }

        @Override
        public void delete(TemporaryQueue queue) {
            // noop
        }
    }

    private static final class InterleavingRefreshResolver implements TemporaryQueueResolver {
        private TemporaryQueueReplyManager.TemporaryReplyQueueDestinationResolver destinationResolver;
        private final AtomicInteger createAttempts = new AtomicInteger();

        void attach(TemporaryQueueReplyManager.TemporaryReplyQueueDestinationResolver destinationResolver) {
            this.destinationResolver = destinationResolver;
        }

        @Override
        public TemporaryQueue createTemporaryQueue(Session session) throws JMSException {
            if (createAttempts.incrementAndGet() == 2) {
                destinationResolver.scheduleRefresh();
            }
            return session.createTemporaryQueue();
        }

        @Override
        public void delete(TemporaryQueue queue) {
            // noop
        }
    }
}
