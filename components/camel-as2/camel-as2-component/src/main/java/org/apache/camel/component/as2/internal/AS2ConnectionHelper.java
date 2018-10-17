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
package org.apache.camel.component.as2.internal;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.as2.AS2Configuration;
import org.apache.camel.component.as2.api.AS2ClientConnection;
import org.apache.camel.component.as2.api.AS2ServerConnection;

/**
 * Utility class for creating AS2 connections.
 */
public final class AS2ConnectionHelper {

    private static Map<Integer, AS2ServerConnection> serverConnections = new HashMap<>();

    /**
     * Prevent instantiation
     */
    private AS2ConnectionHelper() {
    }

    /**
     * Create an AS2 client connection.
     *
     * @param configuration - configuration used to configure connection.
     * @return The AS2 client connection.
     * @throws UnknownHostException Failed to establish connection due to unknown host.
     * @throws IOException - Failed to establish connection.
     */
    public static AS2ClientConnection createAS2ClientConnection(AS2Configuration configuration) throws UnknownHostException, IOException {
        return new AS2ClientConnection(configuration.getAs2Version(), configuration.getUserAgent(), configuration.getClientFqdn(),
                configuration.getTargetHostname(), configuration.getTargetPortNumber());
    }

    /**
     * Create an AS2 server connection.
     *
     * @param configuration - configuration used to configure connection.
     * @return The AS2 server connection.
     * @throws IOException
     */
    public static AS2ServerConnection createAS2ServerConnection(AS2Configuration configuration) throws IOException {
        synchronized (serverConnections) {
            AS2ServerConnection serverConnection = serverConnections.get(configuration.getServerPortNumber());
            if (serverConnection == null) {
                serverConnection = new AS2ServerConnection(configuration.getAs2Version(), configuration.getServer(),
                        configuration.getServerFqdn(), configuration.getServerPortNumber(), configuration.getSigningAlgorithm(),
                        configuration.getSigningCertificateChain(), configuration.getSigningPrivateKey());
                serverConnections.put(configuration.getServerPortNumber(), serverConnection);
            }
            return serverConnection;
        }
    }
}
