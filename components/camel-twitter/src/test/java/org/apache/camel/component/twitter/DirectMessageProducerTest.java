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
package org.apache.camel.component.twitter;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.twitter.directmessage.TwitterDirectMessageEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import twitter4j.Twitter;
import twitter4j.v1.DirectMessagesResources;
import twitter4j.v1.TwitterV1;
import twitter4j.v1.User;
import twitter4j.v1.UsersResources;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The direct message producer sends the body as the text of the direct message, and fails when there is no body.
 */
class DirectMessageProducerTest extends CamelTestSupport {

    private static final String URI = "twitter-directmessage:camelfriend?consumerKey=a&consumerSecret=b&accessToken=c"
                                      + "&accessTokenSecret=d";

    private final UsersResources users = mock(UsersResources.class);
    private final DirectMessagesResources directMessages = mock(DirectMessagesResources.class);

    @BeforeEach
    void setupTwitter() throws Exception {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(users.showUser("camelfriend")).thenReturn(user);
        TwitterV1 v1 = mock(TwitterV1.class);
        when(v1.users()).thenReturn(users);
        when(v1.directMessages()).thenReturn(directMessages);
        Twitter twitter = mock(Twitter.class);
        when(twitter.v1()).thenReturn(v1);
        context.getEndpoint(URI, TwitterDirectMessageEndpoint.class).getProperties().setTwitter(twitter);
    }

    @Test
    void sendsBodyAsText() throws Exception {
        Exchange out = template.send(URI, exchange -> exchange.getIn().setBody("Hello Camel"));

        assertNull(out.getException());
        verify(directMessages).sendDirectMessage(42L, "Hello Camel");
    }

    @Test
    void nullBodyFailsWithoutSending() {
        Exchange out = template.send(URI, exchange -> exchange.getIn().setBody(null));

        assertInstanceOf(InvalidPayloadException.class, out.getException());
        verifyNoInteractions(users, directMessages);
    }
}
