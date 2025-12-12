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
package org.apache.camel.component.jms;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.UUID;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Session;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.infra.artemis.services.RestartAwareArtemisContainer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.support.JmsUtils;
import org.springframework.util.Assert;
import org.testcontainers.containers.Container;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to prove that using org.springframework.jms.supportJmsUtils.commitIfNecessary, if a broker restart or a network
 * issue happens after sending and before executing commit, the message will not be committed and the caller will not be
 * notified, so the route continue with the happy path.
 *
 * https://issues.apache.org/jira/browse/CAMEL-20521
 *
 * https://github.com/spring-projects/spring-framework/issues/32473
 *
 * This issue will be fixed in spring-jms 6.1.6
 */
@Disabled("The test seems to failing often")
public class RestartBrokerBeforeCommitIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RestartBrokerBeforeCommitIT.class);

    protected static final String ARTEMIS_COMMAND = "/home/jboss/broker/bin/artemis";

    protected static final String ARTEMIS_ADDRESS_NAME = "artemis-demo-topic";

    protected static final String ARTEMIS_QUEUE_NAME = "sub1-artemis-demo-topic";

    protected final String SERVICE_ADDRESS = "jms:topic:artemis-demo-topic?jmsMessageType=Text";

    protected RestartAwareArtemisContainer broker;

    @Override
    protected void doPreSetup() throws Exception {
        broker = new RestartAwareArtemisContainer();
        broker.start();
        assertThat(broker.isRunning()).isTrue();
    }

    @Test
    public void commitShouldThrowJmsException() throws Exception {

        int okMessageExpectedCount = 0;
        int jmsExceptionCount = 1;
        int exceptionMessageExpectedCount = 0;

        createQueue();

        MockEndpoint okMock;
        try {
            String uuid = UUID.randomUUID().toString();
            template.send("seda:processMessage", ex -> {
                ex.getMessage().setHeader("TMPL_uuid", uuid);
                ex.getMessage().setBody("Hi!");
            });
            MockEndpoint jmsException = getMockEndpoint("mock:jmsException");
            jmsException.setExpectedMessageCount(jmsExceptionCount);
            okMock = getMockEndpoint("mock:ok");
            okMock.setExpectedMessageCount(okMessageExpectedCount);
            MockEndpoint genericExceptionMock = getMockEndpoint("mock:exception");
            genericExceptionMock.setExpectedMessageCount(exceptionMessageExpectedCount);
            LOG.info("Asserting all mock satisfied");
            MockEndpoint.assertIsSatisfied(context, 10, SECONDS);
            LOG.info("All mock satisfied");
            int actualMessageCountOnQueue = getMessageAdded(broker.adminPort());
            assertThat(actualMessageCountOnQueue).isEqualTo(0);
        } finally {
            broker.stop();
        }
    }

    private void createQueue() throws IOException, InterruptedException {
        Container.ExecResult createQueueResult = broker.execInContainer(ARTEMIS_COMMAND,
                "queue", "create",
                "--user=" + broker.username(),
                "--password=" + broker.password(),
                "--address=" + ARTEMIS_ADDRESS_NAME,
                "--durable",
                "--multicast",
                "--preserve-on-no-consumers",
                "--name=" + ARTEMIS_QUEUE_NAME,
                "--auto-create-address");

        assertThat(createQueueResult.getExitCode()).isZero();
        String err = createQueueResult.getStderr();
        String out = createQueueResult.getStdout();
        LOG.info("STD OUT: {}", out);
        LOG.info("STD ERR: {}", err);
        assertThat(createQueueResult.getExitCode()).isZero();
    }

    protected int getMessageAdded(int webConsolePort) throws URISyntaxException, IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(
                        broker.adminURL() + "/console/jolokia/read/org.apache.activemq.artemis:" +
                             "broker=!%22broker!%22,component=addresses,address=!%22artemis-demo-topic!%22,subcomponent=queues,"
                             +
                             "routing-type=!%22multicast!%22,queue=!%22sub1-artemis-demo-topic!%22/MessagesAdded"))
                .GET()
                .header("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString((broker.username() + ":" + broker.password()).getBytes()))
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isLessThan(300);

        LOG.info("Jolokia Response: ErrorCode: {} Body: {}", response.statusCode(), response.body());

        ObjectMapper mapper = new ObjectMapper();
        JsonObject jsonObject = mapper.readValue(response.body(), JsonObject.class);
        Integer value = jsonObject.getInteger("value");
        assertThat(value).isNotNull();
        return value;
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        //Coonection Factory
        ActiveMQConnectionFactory factory
                = new ActiveMQConnectionFactory(
                        "tcp://" + broker.getHost() + ":" + broker.defaultAcceptorPort(), broker.username(), broker.password());
        registry.bind("factory", factory);

        // Connection Pool
        JmsPoolConnectionFactory jmsPoolConnectionFactory = new JmsPoolConnectionFactory();
        jmsPoolConnectionFactory.setConnectionFactory(factory);
        jmsPoolConnectionFactory.setMaxConnections(20);
        jmsPoolConnectionFactory.setMaxSessionsPerConnection(1);
        jmsPoolConnectionFactory.setUseAnonymousProducers(false);
        jmsPoolConnectionFactory.setConnectionCheckInterval(-1);
        jmsPoolConnectionFactory.setConnectionIdleTimeout(30000);
        registry.bind("connectionPool", jmsPoolConnectionFactory);

        // JMS Component and configuration that use JmsUtil.commitIfNecessary
        final JmsConfiguration jmsConfiguration = getCustomJmsConfiguration(jmsPoolConnectionFactory);
        registry.bind("jmsConfiguration", jmsConfiguration);

        //jms component
        JmsComponent jmsComponent = new JmsComponent();
        jmsComponent.setConfiguration(jmsConfiguration);
        registry.bind("jms", jmsComponent);
    }

    private CustomJmsConfiguration getCustomJmsConfiguration(JmsPoolConnectionFactory jmsPoolConnectionFactory) {
        CustomJmsConfiguration jmsConfiguration = new CustomJmsConfiguration();
        jmsConfiguration.setConnectionFactory(jmsPoolConnectionFactory);
        jmsConfiguration.setTransacted(true);
        jmsConfiguration.setLazyCreateTransactionManager(true);
        jmsConfiguration.setDeliveryPersistent(true);
        jmsConfiguration.setRequestTimeout(10000);
        jmsConfiguration.setReceiveTimeout(1000);
        jmsConfiguration.setCacheLevelName("CACHE_NONE");
        jmsConfiguration.setAcknowledgementModeName("SESSION_TRANSACTED");
        return jmsConfiguration;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:processMessage")
                        .to(SERVICE_ADDRESS)
                        .to("mock:ok")
                        .log(LoggingLevel.INFO, "MESSAGE SENT: ${header.TMPL_uuid}")

                        .onException(JMSException.class)
                        .id("onJMSException")
                        .handled(true)
                        .maximumRedeliveries(0)
                        .logStackTrace(false)
                        .log(LoggingLevel.ERROR,
                                "MESSAGE Failed: ${header.TMPL_uuid} \n***** JMSException received *****: ${exception.message}")
                        .to("mock:jmsException")
                        .end()
                        .onException(Throwable.class)
                        .id("onException")
                        .handled(true)
                        .maximumRedeliveries(0)
                        .to("mock:exception")
                        .end();
            }
        };
    }

    // CUSTOM JMS CONFIGURATION
    class CustomJmsConfiguration extends JmsConfiguration {
        @Override
        protected CamelJmsTemplate createCamelJmsTemplate(ConnectionFactory connectionFactory) {
            return new CustomCamelJmsTemplate(this, connectionFactory);
        }
    }

    // CUSTOM JMS TEMPLATE
    class CustomCamelJmsTemplate extends JmsConfiguration.CamelJmsTemplate {

        public CustomCamelJmsTemplate(JmsConfiguration config, ConnectionFactory connectionFactory) {
            super(config, connectionFactory);
        }

        @Override
        protected void commitIfNecessary(Session session) throws JMSException {
            // Transacted session created by this template -> commit.
            Assert.notNull(session, "Session must not be null");

            // Check commit - avoid commit call within a JTA transaction.
            if (session.getTransacted() && isSessionLocallyTransacted(session)) {
                broker.restart();
                // Transacted session created by this template -> commit.
                JmsUtils.commitIfNecessary(session);
            }
        }
    }
}
