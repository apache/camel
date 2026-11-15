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
package org.apache.camel.component.file.remote.integration;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.EmbeddedConfiguration;
import org.apache.camel.test.infra.ftp.services.embedded.FtpEmbeddedService;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.ListenerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server refuses active mode data connections (PORT), so the listing of the consumer in active mode fails, and the
 * poll must fail too instead of finding an empty directory.
 */
public class FtpConsumerActiveModeRefusedIT extends BaseServerTestSupport {

    @RegisterExtension
    public FtpEmbeddedService service = new FtpEmbeddedService() {
        @Override
        protected FtpServerFactory createFtpServerFactory(EmbeddedConfiguration embeddedConfiguration) {
            FtpServerFactory factory = super.createFtpServerFactory(embeddedConfiguration);
            DataConnectionConfigurationFactory dataConnection = new DataConnectionConfigurationFactory();
            dataConnection.setActiveEnabled(false);
            ListenerFactory listener = new ListenerFactory(factory.getListener(DEFAULT_LISTENER));
            listener.setDataConnectionConfiguration(dataConnection.createDataConnectionConfiguration());
            factory.addListener(DEFAULT_LISTENER, listener.createListener());
            return factory;
        }
    };

    @Test
    public void testRefusedListingFailsThePoll() throws Exception {
        writeFileOnServer();

        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMinimumMessageCount(1);

        context.getRouteController().startRoute("active");

        MockEndpoint.assertIsSatisfied(context);
        GenericFileOperationFailedException cause = error.getReceivedExchanges().get(0)
                .getProperty(Exchange.EXCEPTION_CAUGHT, GenericFileOperationFailedException.class);
        assertNotNull(cause, "The poll should fail as the server refused the listing");
        assertEquals(501, cause.getCode());
        assertTrue(cause.getMessage().contains("Cannot list the current directory"), cause.getMessage());
        assertTrue(cause.getMessage().contains("passiveMode=true"), cause.getMessage());
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter());
    }

    @Test
    public void testPassiveModeConsumesTheFile() throws Exception {
        writeFileOnServer();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:error").expectedMessageCount(0);

        context.getRouteController().startRoute("passive");

        MockEndpoint.assertIsSatisfied(context);
    }

    private void writeFileOnServer() {
        template.sendBodyAndHeader("file://" + service.getFtpRootDir() + "/refused", "Hello World", Exchange.FILE_NAME,
                "hello.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(GenericFileOperationFailedException.class).handled(true).to("mock:error");

                String url = "ftp://admin@localhost:{{ftp.server.port}}/refused?password=admin&delay=100"
                             + "&bridgeErrorHandler=true";
                from(url).routeId("active").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");
                from(url + "&passiveMode=true").routeId("passive").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
