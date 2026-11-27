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
package org.apache.camel.component.milo.server.internal;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.milo.opcua.stack.core.security.CertificateFactory;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.CertificateManager;
import org.eclipse.milo.opcua.stack.core.security.CertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.TrustListManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * A {@link CertificateManager} that delegates to another certificate manager, but whose certificate groups validate the
 * certificates of the clients with the given {@link CertificateValidator}.
 * <p>
 * With Eclipse Milo 1.x, the server validates the certificate of a client with the validator of the certificate group
 * of the server certificate, see {@link CertificateGroup#getCertificateValidator()}.
 */
public final class CertificateValidatorCertificateManager implements CertificateManager {

    private final CertificateManager delegate;
    private final CertificateValidator certificateValidator;

    public CertificateValidatorCertificateManager(final CertificateManager delegate,
                                                  final CertificateValidator certificateValidator) {
        this.delegate = Objects.requireNonNull(delegate);
        this.certificateValidator = Objects.requireNonNull(certificateValidator);
    }

    public CertificateManager getDelegate() {
        return delegate;
    }

    public CertificateValidator getCertificateValidator() {
        return certificateValidator;
    }

    @Override
    public Optional<KeyPair> getKeyPair(final ByteString thumbprint) {
        return delegate.getKeyPair(thumbprint);
    }

    @Override
    public Optional<X509Certificate> getCertificate(final ByteString thumbprint) {
        return delegate.getCertificate(thumbprint);
    }

    @Override
    public Optional<X509Certificate[]> getCertificateChain(final ByteString thumbprint) {
        return delegate.getCertificateChain(thumbprint);
    }

    @Override
    public Optional<CertificateGroup> getCertificateGroup(final ByteString thumbprint) {
        return delegate.getCertificateGroup(thumbprint).map(this::wrap);
    }

    @Override
    public Optional<CertificateGroup> getCertificateGroup(final NodeId certificateGroupId) {
        return delegate.getCertificateGroup(certificateGroupId).map(this::wrap);
    }

    @Override
    public List<CertificateGroup> getCertificateGroups() {
        return delegate.getCertificateGroups().stream().map(this::wrap).toList();
    }

    @Override
    public CertificateQuarantine getCertificateQuarantine() {
        return delegate.getCertificateQuarantine();
    }

    private CertificateGroup wrap(final CertificateGroup group) {
        return new ValidatorCertificateGroup(group, certificateValidator);
    }

    private static final class ValidatorCertificateGroup implements CertificateGroup {

        private final CertificateGroup delegate;
        private final CertificateValidator certificateValidator;

        private ValidatorCertificateGroup(final CertificateGroup delegate, final CertificateValidator certificateValidator) {
            this.delegate = delegate;
            this.certificateValidator = certificateValidator;
        }

        @Override
        public NodeId getCertificateGroupId() {
            return delegate.getCertificateGroupId();
        }

        @Override
        public List<NodeId> getSupportedCertificateTypeIds() {
            return delegate.getSupportedCertificateTypeIds();
        }

        @Override
        public TrustListManager getTrustListManager() {
            return delegate.getTrustListManager();
        }

        @Override
        public List<Entry> getCertificateEntries() {
            return delegate.getCertificateEntries();
        }

        @Override
        public Optional<KeyPair> getKeyPair(final NodeId certificateTypeId) {
            return delegate.getKeyPair(certificateTypeId);
        }

        @Override
        public Optional<X509Certificate[]> getCertificateChain(final NodeId certificateTypeId) {
            return delegate.getCertificateChain(certificateTypeId);
        }

        @Override
        public void updateCertificate(
                final NodeId certificateTypeId, final KeyPair keyPair, final X509Certificate[] certificateChain)
                throws Exception {
            delegate.updateCertificate(certificateTypeId, keyPair, certificateChain);
        }

        @Override
        public CertificateFactory getCertificateFactory() {
            return delegate.getCertificateFactory();
        }

        @Override
        public CertificateValidator getCertificateValidator() {
            return certificateValidator;
        }
    }
}
