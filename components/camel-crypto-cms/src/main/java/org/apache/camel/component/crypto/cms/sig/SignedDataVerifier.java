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
package org.apache.camel.component.crypto.cms.sig;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.common.CryptoCmsUnmarshaller;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsFormatException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoCertificateForSignerInfoException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoCertificateForSignerInfosException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsSignatureException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsSignatureInvalidContentHashException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsVerifierCertificateNotValidException;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.apache.camel.util.IOHelper;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSSignerDigestMismatchException;
import org.bouncycastle.cms.CMSVerifierCertificateNotValidException;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignedDataVerifier extends CryptoCmsUnmarshaller {

    private static final Logger LOG = LoggerFactory.getLogger(SignedDataVerifier.class);

    private final SignedDataVerifierConfiguration conf;

    public SignedDataVerifier(SignedDataVerifierConfiguration config) {
        super(config);
        this.conf = config;
    }

    @Override
    protected Object unmarshalInternal(InputStream is, Exchange exchange) throws Exception {

        CMSSignedDataParser sp;
        try {
            sp = new CMSSignedDataParser(new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(), is);
        } catch (CMSException e) {
            throw new CryptoCmsFormatException(getFormatErrorMessage(), e);
        }
        OutputStreamBuilder output = getOutputStream(sp, exchange);

        debugLog(sp);

        verify(sp, exchange);

        return output.build();
    }

    protected String getFormatErrorMessage() {
        return "Message has invalid format. It was not possible to parse the message into a PKCS7/CMS content info object containing PKCS7/CMS Signed Data.";
    }

    protected OutputStreamBuilder getOutputStream(CMSSignedDataParser sp, Exchange exchange) throws Exception {
        // get the InputStream with the plain data
        InputStream data;
        try {
            data = sp.getSignedContent().getContentStream();
        } catch (NullPointerException e) { // nullpointer exception is
                                           // thrown when the signed content
                                           // is missing
            throw getContentMissingException(e);
        }

        // the input stream must be completely read, otherwise the signer
        // info is not available!
        OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

        try {
            // data can be null in the case of explicit Signed Data
            if (data != null) {
                try {
                    IOHelper.copy(data, osb);
                } finally {
                    IOHelper.close(data);
                }
            }
        } catch (IOException e) {
            throw new CryptoCmsException("Error during reading the signed content of the signed data object", e);
        }
        return osb;
    }

    protected CryptoCmsException getContentMissingException(NullPointerException e) {
        return new CryptoCmsException("PKCS7/CMS signature validation not possible: The content for which the hash-value must be calculated is missing in the PKCS7/CMS signed data instance. "
                                      + "Please check the configuration of the sender of the PKCS7/CMS signature.", e);
    }

    protected void debugLog(CMSSignedDataParser sp) throws CMSException {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        SignerInformationStore signers = sp.getSignerInfos();
        Set<AlgorithmIdentifier> messageDigestAlgorithms = sp.getDigestAlgorithmIDs();
        for (AlgorithmIdentifier algorithm : messageDigestAlgorithms) {
            LOG.debug("Message digest algorithm: {}", algorithm.getAlgorithm().getId());
        }

        LOG.debug("Included Signer Infos:");
        int i = 0;
        for (SignerInformation signer : signers.getSigners()) {
            i++;
            LOG.debug("    Signer {}: {} ", new Object[] {i, signerInformationToString(signer)});
            if (signer.getSignedAttributes() != null) {
                @SuppressWarnings("unchecked")
                Hashtable<String, Attribute> authAttTable = signer.getSignedAttributes().toHashtable();
                if (authAttTable != null) {
                    LOG.debug("    Signed attributes of signer {}: {}", i, attributesToString(authAttTable));
                }
            }
            if (signer.getUnsignedAttributes() != null) {
                @SuppressWarnings("unchecked")
                Hashtable<String, Attribute> unAuthAtts = signer.getUnsignedAttributes().toHashtable();
                if (unAuthAtts != null) {
                    LOG.debug("    Unsigned attributes of signer {}: {}", i, attributesToString(unAuthAtts));
                }
            }
        }
    }

    protected void verify(CMSSignedDataParser signed, Exchange exchange) throws Exception {

        SignerInformationStore signers = getNonEmptySenderInfos(signed);

        Collection<X509Certificate> allowedVerifyCerts = conf.getCertificates(exchange);
        if (allowedVerifyCerts.isEmpty()) {
            throw new CryptoCmsNoCertificateForSignerInfosException("Cannot verify the signatures of the the PKCS7/CMS Signed Data object: No verifier certificate is configured.");
        }

        JcaCertStore certStore = new JcaCertStore(allowedVerifyCerts);

        boolean atLeastOneSignatureVerified = false;
        for (SignerInformation signer : signers.getSigners()) {
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> certCollection = certStore.getMatches(signer.getSID());

            if (certCollection.isEmpty()) {
                if (conf.isVerifySignaturesOfAllSigners(exchange)) {
                    throw new CryptoCmsNoCertificateForSignerInfoException("KCS7/CMS signature verification failed. The public key for the signer information with"
                                                                           + signerInformationToString(signer) + " cannot be found in the configured certificates: "
                                                                           + certsToString(allowedVerifyCerts));
                } else {
                    continue;
                }
            }
            Iterator<X509CertificateHolder> certIt = certCollection.iterator();
            X509CertificateHolder cert = certIt.next();

            try {
                if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(cert))) {
                    LOG.debug("Verification successful");
                    atLeastOneSignatureVerified = true;
                    if (!conf.isVerifySignaturesOfAllSigners(exchange)) {
                        return;
                    }
                } else {
                    throw new CryptoCmsSignatureException("PKCS7/CMS signature verification failed for signer information with " + issuerSerialNumberSubject(cert));
                }
            } catch (CMSSignerDigestMismatchException e) {
                throw new CryptoCmsSignatureInvalidContentHashException("PKCS7/CMS signature verification failed for signer information with " + issuerSerialNumberSubject(cert)
                                                                        + ". Calculated hash differs from the signed hash value. Either the message content does not correspond "
                                                                        + "to the signature or the message might be tampered.", e);
            } catch (CMSVerifierCertificateNotValidException e) {
                throw new CryptoCmsVerifierCertificateNotValidException("PKCS7/CMS signature verification failed for signer information with " + issuerSerialNumberSubject(cert)
                                                                        + ". Certificate was not valid at the signing time.", e);
            }
        }

        if (!atLeastOneSignatureVerified) {
            throw new CryptoCmsNoCertificateForSignerInfosException("Cannot verify the signature of the PKCS7/CMS signed data object with the certificates "
                                                                    + certsToString(allowedVerifyCerts)
                                                                    + " specified in the configuration. The signers in the signed data object are: " + signersToString(signers));

        }
    }

    SignerInformationStore getNonEmptySenderInfos(CMSSignedDataParser signed) throws CryptoCmsException, CMSException {
        SignerInformationStore senders = signed.getSignerInfos();
        if (senders.size() == 0) {
            throw new CryptoCmsException("Sent CMS/PKCS7 signed data message is incorrect. No signer info found in signed data. Correct the sent message.");
        }
        return senders;
    }

    protected String signerInformationToString(SignerInformation sigInfo) {
        if (sigInfo == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ContentTypeOID=");
        sb.append(sigInfo.getContentType());
        sb.append(", Issuer=");
        sb.append(sigInfo.getSID().getIssuer());
        sb.append(", SerialNumber=");
        sb.append(sigInfo.getSID().getSerialNumber());
        sb.append(", SignerInfoVersion=");
        sb.append(sigInfo.getVersion());
        sb.append(", SignatureAlgorithmOID=");
        sb.append(sigInfo.getDigestAlgOID());
        sb.append(", EncryptionAlgorithmOID=");
        sb.append(sigInfo.getEncryptionAlgOID());
        sb.append(", isCounterSignature=");
        sb.append(sigInfo.isCounterSignature());

        return sb.toString();

    }

    protected String signersToString(SignerInformationStore signers) {
        if (signers == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Collection<SignerInformation> sigInfos = signers.getSigners();
        int size = sigInfos.size();
        int counter = 0;
        for (SignerInformation sigInfo : sigInfos) {
            counter++;
            sb.append('[');
            sb.append("Issuer=");
            sb.append(sigInfo.getSID().getIssuer());
            sb.append(", SerialNumber=");
            sb.append(sigInfo.getSID().getSerialNumber());
            sb.append(']');
            if (counter < size) {
                sb.append("; ");
            }
        }
        return sb.toString();
    }

    protected String attributesToString(Hashtable<String, Attribute> attributes) {
        if (attributes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Attribute attr : attributes.values()) {
            sb.append(attr.getAttrType());
            if (CMSAttributes.signingTime.equals(attr.getAttrType()) || CMSAttributes.messageDigest.equals(attr.getAttrType())
                || CMSAttributes.cmsAlgorithmProtect.equals(attr.getAttrType()) || CMSAttributeTableGenerator.CONTENT_TYPE.equals(attr.getAttrType())) {
                // for these attributes we can print the value because we know
                // they do not contain confidential or personal data
                sb.append("=");
                sb.append(attr.getAttrValues());
            }
            sb.append(",");
        }
        return sb.toString();
    }

}
