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
package org.apache.camel.component.netty;

import javax.net.ssl.SSLHandshakeException;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.apache.camel.component.netty.handlers.SslHandshakeFailureHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SslHandshakeFailureHandlerTest {

    @Test
    public void testChannelClosedOnHandshakeFailure() {
        EmbeddedChannel channel = new EmbeddedChannel(SslHandshakeFailureHandler.INSTANCE);
        assertTrue(channel.isOpen());

        SslHandshakeCompletionEvent failureEvent
                = new SslHandshakeCompletionEvent(new SSLHandshakeException("test handshake failure"));
        channel.pipeline().fireUserEventTriggered(failureEvent);

        assertFalse(channel.isOpen(), "Channel should be closed after SSL handshake failure");
    }

    @Test
    public void testChannelOpenOnHandshakeSuccess() {
        EmbeddedChannel channel = new EmbeddedChannel(SslHandshakeFailureHandler.INSTANCE);
        assertTrue(channel.isOpen());

        channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);

        assertTrue(channel.isOpen(), "Channel should remain open after successful SSL handshake");
        channel.close();
    }

    @Test
    public void testHandlerIsSharable() {
        assertTrue(SslHandshakeFailureHandler.INSTANCE.isSharable(),
                "SslHandshakeFailureHandler should be sharable");
    }
}
