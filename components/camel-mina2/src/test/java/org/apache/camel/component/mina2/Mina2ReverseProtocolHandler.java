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
package org.apache.camel.component.mina2;

import org.apache.camel.test.junit4.TestSupport;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

/**
 * {@link IoHandler} implementation of reverser server protocol.
 *
 */
public class Mina2ReverseProtocolHandler extends IoHandlerAdapter {

    public void exceptionCaught(IoSession session, Throwable cause) {
        cause.printStackTrace();
        // Close connection when unexpected exception is caught.
        session.closeNow();
    }

    public void messageReceived(IoSession session, Object message) {
        // Reverse reveiced string
        String str = message.toString();
        StringBuilder buf = new StringBuilder(str.length());
        for (int i = str.length() - 1; i >= 0; i--) {
            buf.append(str.charAt(i));
        }

        if (TestSupport.isPlatform("windows")) {
            // seems to be only required on windows to make it work!
            buf.append(System.lineSeparator());
        }

        // and write it back.
        session.write(buf.toString());
    }
}
