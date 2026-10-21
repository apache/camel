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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * A certificate authority for tests, which issues server certificates and certificate revocation lists.
 */
final class TestPki {

    static final String SERVER_APPLICATION_URI = "urn:camel:milo:test:server";

    private final X500Name name;
    private final KeyPair keyPair;
    private final X509Certificate certificate;
    private long serialNumber;

    TestPki(final String commonName) throws Exception {
        this.name = new X500Name("CN=" + commonName);
        this.keyPair = newKeyPair();

        final Instant now = Instant.now();
        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                this.name, nextSerialNumber(), Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(1, ChronoUnit.DAYS)), this.name, this.keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        this.certificate = sign(builder);
    }

    X509Certificate certificate() {
        return this.certificate;
    }

    /**
     * Issues a certificate for a server with the application URI {@link #SERVER_APPLICATION_URI}.
     */
    X509Certificate issueServerCertificate(final Instant notBefore, final Instant notAfter) throws Exception {
        final X509v3CertificateBuilder builder = serverCertificateBuilder(
                this.name, nextSerialNumber(), notBefore, notAfter, newKeyPair().getPublic(), 0);
        return sign(builder);
    }

    /**
     * Creates a self-signed certificate for a server with the application URI {@link #SERVER_APPLICATION_URI}. As OPC
     * UA requires for a self-signed application certificate, it may sign certificates, since it is its own issuer.
     */
    static X509Certificate selfSignedServerCertificate(final Instant notBefore, final Instant notAfter) throws Exception {
        final KeyPair keyPair = newKeyPair();
        final X509v3CertificateBuilder builder = serverCertificateBuilder(
                new X500Name("CN=Camel Milo Test Server"), BigInteger.ONE, notBefore, notAfter, keyPair.getPublic(),
                KeyUsage.keyCertSign);
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));
    }

    private static X509v3CertificateBuilder serverCertificateBuilder(
            final X500Name issuer, final BigInteger serialNumber, final Instant notBefore, final Instant notAfter,
            final PublicKey publicKey, final int additionalKeyUsage)
            throws Exception {
        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serialNumber, Date.from(notBefore), Date.from(notAfter),
                new X500Name("CN=Camel Milo Test Server"), publicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(
                KeyUsage.digitalSignature | KeyUsage.nonRepudiation | KeyUsage.keyEncipherment | KeyUsage.dataEncipherment
                                                                    | additionalKeyUsage));
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(
                new GeneralName[] {
                        new GeneralName(GeneralName.uniformResourceIdentifier, SERVER_APPLICATION_URI),
                        new GeneralName(GeneralName.dNSName, "localhost") }));
        return builder;
    }

    /**
     * Issues a certificate revocation list that revokes the given certificates.
     */
    X509CRL revocationList(final X509Certificate... revoked) throws Exception {
        final Instant now = Instant.now();
        final X509v2CRLBuilder builder = new X509v2CRLBuilder(this.name, Date.from(now.minus(1, ChronoUnit.HOURS)));
        builder.setNextUpdate(Date.from(now.plus(1, ChronoUnit.DAYS)));
        for (final X509Certificate certificate : revoked) {
            builder.addCRLEntry(certificate.getSerialNumber(), Date.from(now.minus(1, ChronoUnit.HOURS)),
                    CRLReason.keyCompromise);
        }
        return new JcaX509CRLConverter().getCRL(builder.build(signer()));
    }

    private X509Certificate sign(final X509v3CertificateBuilder builder) throws Exception {
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer()));
    }

    private ContentSigner signer() throws Exception {
        return new JcaContentSignerBuilder("SHA256withRSA").build(this.keyPair.getPrivate());
    }

    private BigInteger nextSerialNumber() {
        this.serialNumber++;
        return BigInteger.valueOf(this.serialNumber);
    }

    private static KeyPair newKeyPair() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
