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
package org.apache.camel.component.milo.client.internal;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.component.milo.client.MiloClientConfiguration;
import org.eclipse.milo.opcua.stack.core.security.CertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.FileBasedCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.FileBasedTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects the validator for the certificate of the OPC UA server, and owns the file based trust list when one is
 * configured. The trust list is created on first use and kept until {@link #close()}, so a reconnect reuses it.
 */
final class ServerCertificateTrust implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ServerCertificateTrust.class);

    private final MiloClientConfiguration configuration;
    private final Lock lock = new ReentrantLock();

    private FileBasedTrustListManager trustListManager;
    private CertificateValidator trustListValidator;
    private boolean trustAllLogged;
    private boolean closed;

    ServerCertificateTrust(final MiloClientConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Returns the validator for the certificate of the server that provides the given endpoint.
     *
     * @param  endpoint              the endpoint selected from the discovery result, may be {@code null}
     * @return                       the validator to use for the secure channel, the session and the identity token
     * @throws IllegalStateException if the endpoint uses a security policy other than None and no way to validate the
     *                               server certificate is configured
     * @throws IOException           if the trust list directory cannot be read or created
     */
    CertificateValidator validatorFor(final EndpointDescription endpoint) throws IOException {
        if (this.configuration.getCertificateValidator() != null) {
            return this.configuration.getCertificateValidator();
        }

        final String trustListDirectory = this.configuration.getTrustListDirectory();
        if (trustListDirectory != null && !trustListDirectory.isEmpty()) {
            return trustListValidator(Paths.get(trustListDirectory));
        }

        if (this.configuration.isTrustAllCertificates()) {
            if (isSecured(endpoint)) {
                logTrustAllOnce();
            }
            return new CertificateValidator.InsecureCertificateValidator();
        }

        if (isSecured(endpoint)) {
            throw new IllegalStateException(
                    "The certificate of the OPC UA server cannot be validated: endpoint " + endpoint.getEndpointUrl()
                                            + " uses security policy " + endpoint.getSecurityPolicyUri()
                                            + ", but neither trustListDirectory nor certificateValidator is configured."
                                            + " Configure one of them, or set trustAllCertificates=true to accept any"
                                            + " server certificate (insecure).");
        }

        // SecurityPolicy None: the server is not authenticated in any case, and its certificate is at most used to
        // encrypt the user identity token
        return new CertificateValidator.InsecureCertificateValidator();
    }

    /**
     * Whether the endpoint uses a security policy other than None, in which case the server certificate is validated.
     */
    static boolean isSecured(final EndpointDescription endpoint) {
        return endpoint != null && !SecurityPolicy.None.getUri().equals(endpoint.getSecurityPolicyUri());
    }

    private CertificateValidator trustListValidator(final Path directory) throws IOException {
        this.lock.lock();
        try {
            if (this.closed) {
                throw new IllegalStateException("The trust list in " + directory + " has been closed");
            }
            if (this.trustListValidator == null) {
                final CertificateQuarantine quarantine
                        = FileBasedCertificateQuarantine.create(directory.resolve("rejected").resolve("certs"));
                final FileBasedTrustListManager manager = FileBasedTrustListManager.createAndInitialize(directory);

                if (manager.getTrustedCertificates().isEmpty() && manager.getIssuerCertificates().isEmpty()) {
                    LOG.warn("The trust list in {} contains no certificates, so the certificate of the OPC UA server will be"
                             + " rejected. Add the server certificate, or the certificate of its CA, to {}.",
                            directory, directory.resolve("trusted").resolve("certs"));
                }

                this.trustListManager = manager;
                this.trustListValidator = new DefaultClientCertificateValidator(manager, quarantine);
            }
            return this.trustListValidator;
        } finally {
            this.lock.unlock();
        }
    }

    private void logTrustAllOnce() {
        this.lock.lock();
        try {
            if (this.trustAllLogged) {
                return;
            }
            this.trustAllLogged = true;
        } finally {
            this.lock.unlock();
        }
        LOG.warn("The milo client is configured with trustAllCertificates=true. The certificate of the OPC UA server is"
                 + " not validated, so the client does not authenticate the server it connects to. This should not be"
                 + " used in production environments.");
    }

    @Override
    public void close() throws IOException {
        final FileBasedTrustListManager manager;

        this.lock.lock();
        try {
            this.closed = true;
            manager = this.trustListManager;
            this.trustListManager = null;
            this.trustListValidator = null;
        } finally {
            this.lock.unlock();
        }

        if (manager != null) {
            manager.close();
        }
    }
}
