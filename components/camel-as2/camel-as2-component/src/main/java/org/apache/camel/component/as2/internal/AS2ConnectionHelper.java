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
package org.apache.camel.component.as2.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.component.as2.AS2Configuration;
import org.apache.camel.component.as2.api.AS2AsyncMDNServerConnection;
import org.apache.camel.component.as2.api.AS2ClientConnection;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creating AS2 connections.
 */
public final class AS2ConnectionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(AS2ConnectionHelper.class);

    private static final Map<Integer, AS2ServerConnection> serverConnections = new ConcurrentHashMap<>();

    private static final Map<Integer, AS2AsyncMDNServerConnection> asyncMdnServerConnections = new ConcurrentHashMap<>();

    /**
     * Prevent instantiation
     */
    private AS2ConnectionHelper() {
    }

    /**
     * Create an AS2 client connection.
     *
     * @param  configuration - configuration used to configure connection.
     * @return               The AS2 client connection.
     * @throws IOException   - Failed to establish connection.
     */
    public static AS2ClientConnection createAS2ClientConnection(AS2Configuration configuration) throws IOException {
        return new AS2ClientConnection(
                configuration.getAs2Version(), configuration.getUserAgent(), configuration.getClientFqdn(),
                configuration.getTargetHostname(), configuration.getTargetPortNumber(), configuration.getHttpSocketTimeout(),
                configuration.getHttpConnectionTimeout(), configuration.getHttpConnectionPoolSize(),
                configuration.getHttpConnectionPoolTtl(), configuration.getSslContext(),
                configuration.getHostnameVerifier());
    }

    /**
     * Creates a client connection to receive an AS2 Asynchronous MDN.
     *
     * @param  configuration the configuration used to configure a connection.
     * @return               The AS2 client connection.
     * @throws IOException   if the connection could not be established.
     */
    public static AS2AsyncMDNServerConnection createAS2AsyncMDNServerConnection(AS2Configuration configuration)
            throws IOException {
        try {
            return asyncMdnServerConnections.computeIfAbsent(
                    configuration.getAsyncMdnPortNumber(),
                    key -> {
                        try {
                            return new AS2AsyncMDNServerConnection(
                                    configuration.getAsyncMdnPortNumber(), configuration.getSslContext());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Create an AS2 server connection.
     *
     * @param  configuration - configuration used to configure connection.
     * @return               The AS2 server connection.
     * @throws IOException
     */
    public static AS2ServerConnection createAS2ServerConnection(AS2Configuration configuration) throws IOException {
        try {
            return serverConnections.computeIfAbsent(
                    configuration.getServerPortNumber(),
                    key -> {
                        try {
                            return new AS2ServerConnection(
                                    configuration.getAs2Version(), configuration.getServer(),
                                    configuration.getServerFqdn(), configuration.getServerPortNumber(),
                                    configuration.getSigningAlgorithm(),
                                    configuration.getSigningCertificateChain(), configuration.getSigningPrivateKey(),
                                    configuration.getDecryptingPrivateKey(), configuration.getMdnMessageTemplate(),
                                    configuration.getValidateSigningCertificateChain(), configuration.getSslContext(),
                                    configuration.getMdnUserName(), configuration.getMdnPassword(),
                                    configuration.getMdnAccessToken());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public static void closeAllConnections() {
        closeAllServerConnections();
        closeAllAsyncMdnServerConnections();
    }

    public static void closeAllServerConnections() {
        for (Map.Entry<Integer, AS2ServerConnection> entry : serverConnections.entrySet()) {
            try {
                int port = entry.getKey();
                LOG.debug("Stopping and closing AS2ServerConnection on port: {}", port);
                AS2ServerConnection conn = entry.getValue();
                conn.close();
            } catch (Exception e) {
                // ignore
                LOG.debug("Error stopping and closing AS2ServerConnection due to {}. This exception is ignored",
                        e.getMessage(), e);
            }
        }
        serverConnections.clear();
    }

    public static void closeAllAsyncMdnServerConnections() {
        for (Map.Entry<Integer, AS2AsyncMDNServerConnection> entry : asyncMdnServerConnections.entrySet()) {
            try {
                int port = entry.getKey();
                LOG.debug("Stopping and closing AsyncMdnServerConnection on port: {}", port);
                entry.getValue().close();
            } catch (Exception e) {
                LOG.debug("Error stopping and closing AsyncMdnServerConnection due to {}. This exception is ignored",
                        e.getMessage(), e);
            }
        }
        asyncMdnServerConnections.clear();
    }
}
