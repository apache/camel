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

import org.apache.camel.spi.Metadata;

/**
 * Netty constants
 */
public final class NettyConstants {
    @Metadata(description = "Indicates whether the channel should be closed after complete.", javaType = "Boolean")
    public static final String NETTY_CLOSE_CHANNEL_WHEN_COMPLETE = "CamelNettyCloseChannelWhenComplete";
    @Metadata(description = "The channel handler context.", javaType = "io.netty.channel.ChannelHandlerContext")
    public static final String NETTY_CHANNEL_HANDLER_CONTEXT = "CamelNettyChannelHandlerContext";
    public static final String NETTY_MESSAGE_EVENT = "CamelNettyMessageEvent";
    @Metadata(description = "The remote address.", javaType = "java.net.SocketAddress")
    public static final String NETTY_REMOTE_ADDRESS = "CamelNettyRemoteAddress";
    @Metadata(description = "The local address.", javaType = "java.net.SocketAddress")
    public static final String NETTY_LOCAL_ADDRESS = "CamelNettyLocalAddress";
    @Metadata(description = "The SSL session.", javaType = "javax.net.ssl.SSLSession")
    public static final String NETTY_SSL_SESSION = "CamelNettySSLSession";
    @Metadata(description = "The SSL client certificate subject name.", javaType = "String")
    public static final String NETTY_SSL_CLIENT_CERT_SUBJECT_NAME = "CamelNettySSLClientCertSubjectName";
    @Metadata(description = "The SSL client certificate issuer name.", javaType = "String")
    public static final String NETTY_SSL_CLIENT_CERT_ISSUER_NAME = "CamelNettySSLClientCertIssuerName";
    @Metadata(description = "The SSL client certificate serial number.", javaType = "String")
    public static final String NETTY_SSL_CLIENT_CERT_SERIAL_NO = "CamelNettySSLClientCertSerialNumber";
    @Metadata(description = "The SSL client certificate not before.", javaType = "java.util.Date")
    public static final String NETTY_SSL_CLIENT_CERT_NOT_BEFORE = "CamelNettySSLClientCertNotBefore";
    @Metadata(description = "The SSL client certificate not after.", javaType = "java.util.Date")
    public static final String NETTY_SSL_CLIENT_CERT_NOT_AFTER = "CamelNettySSLClientCertNotAfter";
    @Metadata(description = "The read timeout.", javaType = "Long")
    public static final String NETTY_REQUEST_TIMEOUT = "CamelNettyRequestTimeout";
    public static final String NETTY_CHANNEL = "CamelNettyChannel";
    public static final String NETTY_CLIENT_CONTINUE = "CamelClientContinue";

    private NettyConstants() {
        // Utility class
    }

}
