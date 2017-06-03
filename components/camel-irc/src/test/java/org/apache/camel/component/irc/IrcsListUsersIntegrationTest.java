/**
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
package org.apache.camel.component.irc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for the {@link IrcConfiguration#isNamesOnJoin()} option.
 * Joins a channel and asserts that the username of the current test user is
 * listed for the channel.
 */
public class IrcsListUsersIntegrationTest extends CamelTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrcsListUsersIntegrationTest.class);

    /** message code for a reply to a <code>NAMES</code> command. */
    private static final String IRC_RPL_NAMREPLY = "353";

    /** irc component uri. configured by properties */
    private static final String PRODUCER_URI = "ircs:{{test.user}}@{{test.server}}/{{test.room}}";

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    protected Properties properties;

    public IrcsListUsersIntegrationTest() throws IOException {
        super();
        properties = new Properties();
        InputStream resourceAsStream = this.getClass().getResourceAsStream("/it-list-users.properties");
        properties.load(resourceAsStream);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                LOGGER.debug("Creating new test route");
                
                from(PRODUCER_URI + "?namesOnJoin=true&onReply=true")
                    .choice()
                        .when(header("irc.messageType").isEqualToIgnoreCase("REPLY"))
                            .filter(header("irc.num").isEqualTo(IRC_RPL_NAMREPLY))
                            .to("mock:result").stop();
            }
        };
    }

    @Test
    public void test() throws Exception {
        resultEndpoint.setMinimumExpectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();
        String body = resultEndpoint.getExchanges().get(0).getIn().getBody(String.class);
        LOGGER.debug("Received usernames: [{}]", body);
        String username = properties.getProperty("test.user");
        assertTrue("userlist does not contain test user", body.contains(username));
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return properties;
    }

}
