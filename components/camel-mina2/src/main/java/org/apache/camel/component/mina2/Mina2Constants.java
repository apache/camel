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

/**
 * Mina constants
 *
 * @version 
 */
public final class Mina2Constants {

    public static final String MINA_CLOSE_SESSION_WHEN_COMPLETE = "CamelMina2CloseSessionWhenComplete";
    /** The key of the IoSession which is stored in the message header*/
    public static final String MINA_IOSESSION = "CamelMina2IoSession";
    /** The socket address of local machine that received the message. */
    public static final String MINA_LOCAL_ADDRESS = "CamelMina2LocalAddress";
    /** The socket address of the remote machine that send the message. */
    public static final String MINA_REMOTE_ADDRESS = "CamelMina2RemoteAddress";

    private Mina2Constants() {
        // Utility class
    }
}
