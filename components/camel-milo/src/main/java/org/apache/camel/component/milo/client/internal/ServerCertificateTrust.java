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
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.component.milo.client.MiloClientConfiguration;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.FileBasedCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.FileBasedTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.validation.ValidationCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects the validator for the certificate of the OPC UA server, and owns the file based trust list when one is
 * configured. The trust list is created on first use and kept until {@link #close()}, so a reconnect reuses it.
 */
final class ServerCertificateTrust implements Closeable {

    /**
     * The checks of the trust list validator besides the chain of trust. As OPC UA requires, a certificate issued by a
     * CA is refused when the trust list holds no revocation list of that CA. A self-signed certificate of the trust
     * list is not checked for revocation.
     */
    static final Set<ValidationCheck> TRUST_LIST_CHECKS
            = Set.of(ValidationCheck.APPLICATION_URI, ValidationCheck.VALIDITY, ValidationCheck.REVOCATION);

    private static final Set<Long> CERTIFICATE_STATUS_CODES = Set.of(
            StatusCodes.Bad_SecurityChecksFailed,
            StatusCodes.Bad_CertificateInvalid,
            StatusCodes.Bad_CertificateUntrusted,
            StatusCodes.Bad_CertificateTimeInvalid,
            StatusCodes.Bad_CertificateIssuerTimeInvalid,
            StatusCodes.Bad_CertificateHostNameInvalid,
            StatusCodes.Bad_CertificateUriInvalid,
            StatusCodes.Bad_CertificateUseNotAllowed,
            StatusCodes.Bad_CertificateIssuerUseNotAllowed,
            StatusCodes.Bad_CertificateRevocationUnknown,
            StatusCodes.Bad_CertificateIssuerRevocationUnknown,
            StatusCodes.Bad_CertificateRevoked,
            StatusCodes.Bad_CertificateIssuerRevoked,
            StatusCodes.Bad_CertificateChainIncomplete,
            StatusCodes.Bad_CertificatePolicyCheckFailed);

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
     * @param  endpoint               the endpoint selected from the discovery result, may be {@code null}
     * @return                        the validator to use for the secure channel, the session and the identity token
     * @throws NotConfiguredException if the endpoint uses a security policy other than None and no way to validate the
     *                                server certificate is configured
     * @throws IOException            if the trust list directory cannot be read or created
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
            throw new NotConfiguredException(
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

    /**
     * Finds the reason of a connection failure that a retry does not fix by itself: a missing trust configuration, or a
     * certificate or security check that failed, on the client or on the server side.
     *
     * @param  failure the failure of a connection attempt
     * @return         the exception that describes the reason, or {@code null} for any other failure
     */
    static Throwable findCertificateFailure(final Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof NotConfiguredException) {
                return current;
            }
            if (current instanceof UaException uaException
                    && CERTIFICATE_STATUS_CODES.contains(uaException.getStatusCode().getValue())) {
                return current;
            }
            current = current.getCause();
        }
        return null;
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
                this.trustListValidator = new DefaultClientCertificateValidator(manager, TRUST_LIST_CHECKS, quarantine);
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

    /**
     * The endpoint uses a security policy other than None, but no way to validate the server certificate is configured.
     * Retrying does not help until the configuration changes.
     */
    static final class NotConfiguredException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        NotConfiguredException(final String message) {
            super(message);
        }
    }
}
