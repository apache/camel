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
package org.apache.camel.component.mina;

import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.isPlatform;

/**
 * {@link IoHandler} implementation of reverser server protocol.
 *
 */
public class MinaReverseProtocolHandler extends IoHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(MinaReverseProtocolHandler.class);

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        LOG.warn("Unhandled exception: {}", cause.getMessage(), cause);
        // Close connection when unexpected exception is caught.
        session.closeNow();
    }

    @Override
    public void messageReceived(IoSession session, Object message) {
        // Reverse reveiced string
        String str = message.toString();
        StringBuilder buf = new StringBuilder(str.length());
        for (int i = str.length() - 1; i >= 0; i--) {
            buf.append(str.charAt(i));
        }

        if (isPlatform("windows")) {
            // seems to be only required on windows to make it work!
            buf.append(System.lineSeparator());
        }

        // and write it back.
        session.write(buf.toString());
    }
}
