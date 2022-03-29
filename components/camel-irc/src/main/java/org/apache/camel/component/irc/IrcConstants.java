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
package org.apache.camel.component.irc;

import org.apache.camel.spi.Metadata;

/**
 * IRC Constants
 */
public final class IrcConstants {
    @Metadata(description = "The type of message", javaType = "String")
    public static final String IRC_MESSAGE_TYPE = "irc.messageType";
    @Metadata(description = "The target", javaType = "String")
    public static final String IRC_TARGET = "irc.target";
    @Metadata(description = "The nickname or channel the message should be sent to.", javaType = "String")
    public static final String IRC_SEND_TO = "irc.sendTo";
    @Metadata(description = "The nickname of the user who is kicked from a channel (passive).", javaType = "String")
    public static final String IRC_USER_KICKED = "irc.user.kicked";
    @Metadata(description = "The host of the person who sent the line.", javaType = "String")
    public static final String IRC_USER_HOST = "irc.user.host";
    @Metadata(description = "The nickname of the person who sent the line or the server name of the server which sent the line.",
              javaType = "String")
    public static final String IRC_USER_NICK = "irc.user.nick";
    @Metadata(description = "The server name of the server which sent the line or the nickname of the person who sent the line.",
              javaType = "String")
    public static final String IRC_USER_SERVERNAME = "irc.user.servername";
    @Metadata(description = "The username of the person who sent the line.", javaType = "String")
    public static final String IRC_USER_USERNAME = "irc.user.username";
    @Metadata(description = "The numeric reply.", javaType = "int")
    public static final String IRC_NUM = "irc.num";
    @Metadata(description = "The first part of the message", javaType = "String")
    public static final String IRC_VALUE = "irc.value";

    private IrcConstants() {
        // Utility class
    }
}
