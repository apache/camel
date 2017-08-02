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
package org.apache.camel.component.crypto.cms.crypt;

import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.common.CryptoCmsUnmarshaller;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsFormatException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoCertificateForRecipientsException;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.apache.camel.util.IOHelper;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.cert.X509CRLEntryHolder;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSEnvelopedDataParser;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.KeyTransRecipientId;
import org.bouncycastle.cms.KeyTransRecipientInformation;
import org.bouncycastle.cms.OriginatorInformation;
import org.bouncycastle.cms.Recipient;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientId;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for decrypting CMS EnvelopedData content.
 */
public class EnvelopedDataDecryptor extends CryptoCmsUnmarshaller {
    private static final Logger LOG = LoggerFactory.getLogger(EnvelopedDataDecryptor.class);

    private final EnvelopedDataDecryptorConfiguration conf;

    // private final PublicKeyFinder finder;

    public EnvelopedDataDecryptor(EnvelopedDataDecryptorConfiguration conf) {
        super(conf);
        this.conf = conf;
    }

    @Override
    protected Object unmarshalInternal(InputStream is, Exchange exchange) throws Exception {
        CMSEnvelopedDataParser parser;
        try {
            parser = new CMSEnvelopedDataParser(is);
        } catch (CMSException e) {
            throw new CryptoCmsFormatException(getFormatErrorMessage(), e);
        } catch (NullPointerException e) {
            // occurs with an empty payloud
            throw new CryptoCmsFormatException(getFormatErrorMessage(), e);
        }
        return unmarshal(parser, exchange);
    }

    String getFormatErrorMessage() {
        return "Message has invalid format. It was not possible to parse the message into a PKCS7/CMS enveloped data object.";
    }

    private Object unmarshal(CMSEnvelopedDataParser parser, Exchange exchange) throws Exception {
        LOG.debug("Decrypting CMS Enveloped Data started");
        debugLog(parser);
        RecipientInformationStore recipientsStore = parser.getRecipientInfos();

        if (recipientsStore.size() == 0) {
            throw new CryptoCmsException("PKCS7/CMS enveloped data message is incorrect. No recipient information found in enveloped data.");
        }

        // we loop over the key-pairs in the keystore and use the first entry
        // which fits to a recipient info
        RecipientInformation recipientInformation = null;
        Collection<PrivateKeyWithCertificate> privateKeyCerts = conf.getPrivateKeyCertificateCollection(exchange);
        if (privateKeyCerts.isEmpty()) {
            throw new CryptoCmsNoCertificateForRecipientsException("Cannot decrypt PKCS7/CMS enveloped data object. No private key for the decryption configured.");

        }
        PrivateKey foundPrivateKey = null;
        for (PrivateKeyWithCertificate privateKeyCert : privateKeyCerts) {
            X509Certificate cert = privateKeyCert.getCertificate();
            JceKeyTransRecipientId recipientId = new JceKeyTransRecipientId(cert);
            recipientInformation = recipientsStore.get(recipientId);
            if (recipientInformation != null) {

                LOG.debug("Recipient found for certificate with subjectDN={}, issuerDN={}, and serial number={}",
                          new Object[] {cert.getSubjectDN(), cert.getIssuerDN(), cert.getSerialNumber()});
                foundPrivateKey = privateKeyCert.getPrivateKey();
                break; // use the first found private key
            }
        }
        if (recipientInformation == null) {
            List<X509Certificate> certs = new ArrayList<>(privateKeyCerts.size());
            for (PrivateKeyWithCertificate pc : privateKeyCerts) {
                certs.add(pc.getCertificate());
            }
            throw new CryptoCmsNoCertificateForRecipientsException("Cannot decrypt PKCS7/CMS enveloped data object. No certificate found among the configured certificates "
                                                                   + "which fit to one of the recipients in the enveloped data object. The recipients in the enveloped data object are: "
                                                                   + recipientsToString(recipientsStore.getRecipients()) + "The configured certificates are: "
                                                                   + certsToString(certs)
                                                                   + ". Specify a certificate with private key which fits to one of the recipients in the configruation or"
                                                                   + " check whether the encrypted message is encrypted with the correct key.");
        }

        Recipient recipient = new JceKeyTransEnvelopedRecipient(foundPrivateKey);

        // get the InputStream with the decrypted data, here the decryption
        // takes place
        InputStream is;
        try {
            is = recipientInformation.getContentStream(recipient).getContentStream();
        } catch (CMSException | IOException e) {
            throw new CryptoCmsException("Error during decrypting an enveloped data object", e);
        }

        Object result;
        try {
            result = transformToStreamCacheOrByteArray(exchange, is);
        } finally {
            IOHelper.close(is);
        }
        if (LOG.isDebugEnabled()) {
            // unprotected attributes can only be read after the parsing is
            // finished.
            AttributeTable unprotectedAttsTable = parser.getUnprotectedAttributes();
            if (unprotectedAttsTable != null) {
                LOG.debug("Unprotected attributes size {}", unprotectedAttsTable.size());
                @SuppressWarnings("unchecked")
                Hashtable<String, Attribute> unprotectedAtts = unprotectedAttsTable.toHashtable();
                if (unprotectedAtts != null) {
                    LOG.debug("Unprotected attributes: {}", attributesToString(unprotectedAtts));
                }
            }
        }
        return result;

    }

    protected void debugLog(CMSEnvelopedDataParser parser) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        OriginatorInformation originatorInfo = parser.getOriginatorInfo();
        if (originatorInfo != null) {
            LOG.debug("Enveloped Data has originator information");
            @SuppressWarnings("unchecked")
            Store<X509CertificateHolder> certStore = originatorInfo.getCertificates();
            Collection<X509CertificateHolder> certs = certStore.getMatches(null);
            if (certs != null && certs.size() > 0) {
                LOG.debug("Certificates in the originator information:");
                for (X509CertificateHolder cert : certs) {
                    LOG.debug("    subject=" + cert.getSubject() + ", issuer=" + cert.getIssuer() + ", serial number=" + cert.getSerialNumber());
                }
            }
            @SuppressWarnings("unchecked")
            Store<X509CRLHolder> crlsStore = originatorInfo.getCRLs();
            Collection<X509CRLHolder> crls = crlsStore.getMatches(null);
            if (crls != null && crls.size() > 0) {
                LOG.debug("CRLs in the originator information:");
                for (X509CRLHolder crl : crls) {
                    LOG.debug("    CRL issuer={}", crl.getIssuer());
                    @SuppressWarnings("unchecked")
                    Collection<X509CRLEntryHolder> revokedCerts = crl.getRevokedCertificates();
                    for (X509CRLEntryHolder revokedCert : revokedCerts) {
                        LOG.debug("        Revoked Certificate: issuer=" + revokedCert.getCertificateIssuer() + ", serial number=" + revokedCert.getSerialNumber() + ", date="
                                  + revokedCert.getRevocationDate());
                    }
                }
            }
        }
        LOG.debug("Content encryption algorithm OID: {}", parser.getEncryptionAlgOID());

        LOG.debug("Recipient Infos:");
        RecipientInformationStore recipientStore = parser.getRecipientInfos();
        Collection<RecipientInformation> recipients = recipientStore.getRecipients();
        int counter = 0;
        for (RecipientInformation recipient : recipients) {
            counter++;
            LOG.debug("   Recipient Info {}: {}", counter, recipientToString(recipient));
        }
    }

    protected String recipientsToString(Collection<RecipientInformation> recipients) {
        StringBuilder sb = new StringBuilder();
        int counter = 0;
        int size = recipients.size();
        for (RecipientInformation recipient : recipients) {
            counter++;
            sb.append('[');
            sb.append(recipientToString(recipient));
            sb.append(']');
            if (counter < size) {
                sb.append(';');
            }
        }
        return sb.toString();
    }

    protected String recipientToString(RecipientInformation recipient) {
        if (recipient instanceof KeyTransRecipientInformation) {
            KeyTransRecipientId rid = (KeyTransRecipientId)recipient.getRID();
            return "Issuer=" + rid.getIssuer() + ", serial number=" + rid.getSerialNumber() + ", key encryption algorithm OID=" + recipient.getKeyEncryptionAlgOID();
        } else {
            return "not a KeyTransRecipientInformation: " + recipient.getRID().getType();
        }
    }

    protected String attributesToString(Hashtable<String, Attribute> attributes) {
        if (attributes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int size = attributes.size();
        int counter = 0;
        for (Attribute attr : attributes.values()) {
            counter++;
            sb.append(attr.getAttrType());
            // we do not print out the attribute value because the value may
            // contain sensitive information
            if (counter < size) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private Object transformToStreamCacheOrByteArray(Exchange exchange, InputStream is) throws CryptoCmsException {
        // the input stream must be completely read, outherwise you will get
        // errors when your use as next component the file adapter.
        OutputStreamBuilder output = OutputStreamBuilder.withExchange(exchange);
        try {
            // data can be null in the case of explicit Signed Data
            if (is != null) {
                try {
                    IOHelper.copy(is, output);
                } finally {
                    IOHelper.close(is);
                }
            }

            LOG.debug("CMS Enveloped Data decryption successful");
            return output.build();
        } catch (IOException e) {
            throw new CryptoCmsException("Error during reading the unencrypted content of the enveloped data object", e);
        } finally {
            IOHelper.close(output);
        }
    }

}
