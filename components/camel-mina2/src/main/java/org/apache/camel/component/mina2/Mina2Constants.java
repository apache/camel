/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.camel.component.mina2;

/**
 * Mina constants
 *
 * @version
 */
public final class Mina2Constants {

    public static final transient String MINA2_CLOSE_SESSION_WHEN_COMPLETE = "CamelMina2CloseSessionWhenComplete";
    /**
     * The key of the IoSession which is stored in the message header
     */
    public static final transient String MINA2_IOSESSION = "CamelMina2IoSession";
    /**
     * The socket address of local machine that received the message.
     */
    public static final transient String MINA2_LOCAL_ADDRESS = "CamelMina2LocalAddress";
    /**
     * The socket address of the remote machine that send the message.
     */
    public static final transient String MINA2_REMOTE_ADDRESS = "CamelMina2RemoteAddress";
    /**
     * Set to true and passed to in the exchange when the session is created.
     */
    public static final transient String MINA2_SESSION_CREATED = "CamelMina2SessionCreated";
    /**
     * Set to true and passed to in the exchange when the session is created.
     */
    public static final transient String MINA2_SESSION_OPENED = "CamelMina2SessionOpened";
    /**
     * Set to true and passed to in the exchange when the session is created.
     */
    public static final transient String MINA2_SESSION_CLOSED = "CamelMina2SessionClosed";
    /**
     * Set to true and passed to in the exchange when the session is created.
     */
    public static final transient String MINA2_SESSION_IDLE = "CamelMina2SessionIdleÏ";

    private Mina2Constants() {
        // Utility class
    }
}
