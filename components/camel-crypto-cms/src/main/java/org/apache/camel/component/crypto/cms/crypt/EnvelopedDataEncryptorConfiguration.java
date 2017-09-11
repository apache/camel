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

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

import org.apache.camel.CamelContext;
import org.apache.camel.component.crypto.cms.common.AttributesGeneratorProvider;
import org.apache.camel.component.crypto.cms.common.CryptoCmsMarshallerConfiguration;
import org.apache.camel.component.crypto.cms.common.OriginatorInformationProvider;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cms.CMSAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class EnvelopedDataEncryptorConfiguration extends CryptoCmsMarshallerConfiguration {

    private static final String CAST5_CBC_PKCS5_PADDING = "CAST5/CBC/PKCS5Padding";

    private static final String RC2_CBC_PKCS5_PADDING = "RC2/CBC/PKCS5Padding";

    private static final String CAMELLIA_CBC_PKCS5_PADDING = "Camellia/CBC/PKCS5Padding";

    private static final String AES_CBC_PKCS5_PADDING = "AES/CBC/PKCS5Padding";

    private static final String DES_CBC_PKCS5_PADDING = "DES/CBC/PKCS5Padding";

    private static final String DESEDE_CBC_PKCS5_PADDING = "DESede/CBC/PKCS5Padding";

    private static final Logger LOG = LoggerFactory.getLogger(EnvelopedDataEncryptorConfiguration.class);

    private static final Map<String, List<Integer>> SUPPORTED_ENCRYPTION_ALGORITHMS = new HashMap<String, List<Integer>>(7);

    static {

        List<Integer> allowedKeyLengthForAESandCamellia;
        if (isLimitedEncryptionStrength()) {
            allowedKeyLengthForAESandCamellia = Arrays.asList(new Integer[] {128});
        } else {
            allowedKeyLengthForAESandCamellia = Arrays.asList(new Integer[] {256, 192, 128});
        }

        SUPPORTED_ENCRYPTION_ALGORITHMS.put(DESEDE_CBC_PKCS5_PADDING, Arrays.asList(new Integer[] {192, 128}));
        SUPPORTED_ENCRYPTION_ALGORITHMS.put(DES_CBC_PKCS5_PADDING, Arrays.asList(new Integer[] {64, 56}));
        SUPPORTED_ENCRYPTION_ALGORITHMS.put(AES_CBC_PKCS5_PADDING, allowedKeyLengthForAESandCamellia);
        SUPPORTED_ENCRYPTION_ALGORITHMS.put(CAMELLIA_CBC_PKCS5_PADDING, allowedKeyLengthForAESandCamellia);
        SUPPORTED_ENCRYPTION_ALGORITHMS.put(RC2_CBC_PKCS5_PADDING, Arrays.asList(new Integer[] {128, 120, 112, 104, 96, 88, 80, 72, 64, 56, 48, 40}));
        SUPPORTED_ENCRYPTION_ALGORITHMS.put(CAST5_CBC_PKCS5_PADDING, Arrays.asList(new Integer[] {128, 120, 112, 104, 96, 88, 80, 72, 64, 56, 48, 40}));
    }

    @UriParam(label = "encrypt", multiValue = true, description = "Recipient Info: reference to a bean which implements the interface org.apache.camel.component.crypto.cms.api.TransRecipientInfo")
    private final List<RecipientInfo> recipient = new ArrayList<RecipientInfo>(3);

    @UriParam(label = "encrypt", enums = "AES/CBC/PKCS5Padding,DESede/CBC/PKCS5Padding,Camellia/CBC/PKCS5Padding,CAST5/CBC/PKCS5Padding")
    private String contentEncryptionAlgorithm;

    @UriParam(label = "encrypt")
    private int secretKeyLength;

    @UriParam(label = "encrypt", defaultValue = "null")
    private AttributesGeneratorProvider unprotectedAttributesGeneratorProvider;

    @UriParam(label = "encrypt", defaultValue = "null")
    private OriginatorInformationProvider originatorInformationProvider;

    // calculated parameters
    private ASN1ObjectIdentifier algorithmId;

    public EnvelopedDataEncryptorConfiguration(CamelContext context) {
        super(context);
    }

    private static boolean isLimitedEncryptionStrength() {
        // limited encryption strength
        boolean limitedEncryptionStrength;
        try {
            limitedEncryptionStrength = Cipher.getMaxAllowedKeyLength("AES") < 256;
        } catch (NoSuchAlgorithmException e) {
            // should never occur
            throw new IllegalStateException(e);
        }
        return limitedEncryptionStrength;
    }

    public List<RecipientInfo> getRecipient() {
        return recipient;
    }

    public void setRecipient(RecipientInfo recipient) {
        this.recipient.add(recipient);
    }

    // for multi values
    public void setRecipient(List<?> recipients) {
        if (recipients == null) {
            return;
        }
        for (Object recipientOb : recipients) {
            if (recipientOb instanceof String) {
                String recipientName = (String)recipientOb;
                String valueNoHash = recipientName.replaceAll("#", "");
                if (getContext() != null && recipientName != null) {
                    RecipientInfo recipient = getContext().getRegistry().lookupByNameAndType(valueNoHash, RecipientInfo.class);
                    if (recipient != null) {
                        setRecipient(recipient);
                    }
                }
            }
        }

    }

    public String getContentEncryptionAlgorithm() {
        return contentEncryptionAlgorithm;
    }

    /**
     * Encryption algorithm, for example "DESede/CBC/PKCS5Padding". Further
     * possible values: DESede/CBC/PKCS5Padding, AES/CBC/PKCS5Padding,
     * Camellia/CBC/PKCS5Padding, CAST5/CBC/PKCS5Padding.
     */
    public void setContentEncryptionAlgorithm(String contentEncryptionAlgorithm) {
        this.contentEncryptionAlgorithm = contentEncryptionAlgorithm;
    }

    public int getSecretKeyLength() {
        return secretKeyLength;
    }

    /**
     * Key length for the secret symmetric key used for the content encryption.
     * Only used if the specified content-encryption algorithm allows keys of
     * different sizes. If contentEncryptionAlgorithm=AES/CBC/PKCS5Padding or
     * Camellia/CBC/PKCS5Padding then 128; if
     * contentEncryptionAlgorithm=DESede/CBC/PKCS5Padding then 192, 128; if
     * strong encryption is enabled then for AES/CBC/PKCS5Padding and
     * Camellia/CBC/PKCS5Padding also the key lengths 192 and 256 are possible.
     */
    public void setSecretKeyLength(int secretKeyLength) {
        this.secretKeyLength = secretKeyLength;
    }

    public AttributesGeneratorProvider getUnprotectedAttributesGeneratorProvider() {
        return unprotectedAttributesGeneratorProvider;
    }

    /**
     * Provider of the generator for the unprotected attributes. The default
     * value is <code>null</code> which means no unprotected attribute is added
     * to the Enveloped Data object. See
     * https://tools.ietf.org/html/rfc5652#section-6.1.
     */
    public void setUnprotectedAttributesGeneratorProvider(AttributesGeneratorProvider unprotectedAttributeTableGeneratorProvider) {
        this.unprotectedAttributesGeneratorProvider = unprotectedAttributeTableGeneratorProvider;
    }

    public OriginatorInformationProvider getOriginatorInformationProvider() {
        return originatorInformationProvider;
    }

    /**
     * Provider for the originator info. See
     * https://tools.ietf.org/html/rfc5652#section-6.1. The default value is
     * <code>null</code>.
     */
    public void setOriginatorInformationProvider(OriginatorInformationProvider originatorInformationProvider) {
        this.originatorInformationProvider = originatorInformationProvider;
    }

    public void init() throws CryptoCmsException {
        if (recipient.size() == 0) {
            logErrorAndThrow(LOG, "No recipient configured.");
        }
        checkEncryptionAlgorithmAndSecretKeyLength();

        calcualteAlgorithmIdWithKeyLength();
    }

    private void checkEncryptionAlgorithmAndSecretKeyLength() throws CryptoCmsException {
        if (contentEncryptionAlgorithm == null) {
            logErrorAndThrow(LOG, "Content encryption algorithm is null");
        } else if (!SUPPORTED_ENCRYPTION_ALGORITHMS.keySet().contains(contentEncryptionAlgorithm)) {
            logErrorAndThrow(LOG, "Content encryption algorithm " + contentEncryptionAlgorithm + " not supported");
        } else if (!SUPPORTED_ENCRYPTION_ALGORITHMS.get(contentEncryptionAlgorithm).contains(secretKeyLength)) {
            logErrorAndThrow(LOG, "Content encryption algorithm " + contentEncryptionAlgorithm + " does not supported secretKeyLength of " + secretKeyLength);
        }
    }

    private void calcualteAlgorithmIdWithKeyLength() {

        if (DESEDE_CBC_PKCS5_PADDING.equals(getContentEncryptionAlgorithm())) {

            algorithmId = CMSAlgorithm.DES_EDE3_CBC;

        } else if (DES_CBC_PKCS5_PADDING.equals(getContentEncryptionAlgorithm())) {

            algorithmId = CMSAlgorithm.DES_CBC;

        } else if (AES_CBC_PKCS5_PADDING.equals(getContentEncryptionAlgorithm())) {

            switch (getSecretKeyLength()) {
            case 256:
                algorithmId = CMSAlgorithm.AES256_CBC;
                break;
            case 192:
                algorithmId = CMSAlgorithm.AES192_CBC;
                break;
            case 128:
                algorithmId = CMSAlgorithm.AES128_CBC;
                break;

            default:
                // should not happen, has already been checked
                throw new IllegalStateException("Unsupported secret key length " + getSecretKeyLength() + " for algorithm AES");
            }

        } else if (CAMELLIA_CBC_PKCS5_PADDING.equals(getContentEncryptionAlgorithm())) {

            switch (getSecretKeyLength()) {
            case 256:
                algorithmId = CMSAlgorithm.CAMELLIA256_CBC;
                break;
            case 192:
                algorithmId = CMSAlgorithm.CAMELLIA192_CBC;
                break;
            case 128:
                algorithmId = CMSAlgorithm.CAMELLIA128_CBC;
                break;
            default:
                // should not happen, has already been checked
                throw new IllegalStateException("Unsupported secret key length " + getSecretKeyLength() + " for algorithm Camellia");
            }

        } else if (RC2_CBC_PKCS5_PADDING.equals(getContentEncryptionAlgorithm())) {

            algorithmId = CMSAlgorithm.RC2_CBC;

        } else if (CAST5_CBC_PKCS5_PADDING.equals(getContentEncryptionAlgorithm())) {

            algorithmId = CMSAlgorithm.CAST5_CBC;

        } else {
            // should not occur, has already been checked
            throw new IllegalStateException("Content encryption algorithm " + getContentEncryptionAlgorithm() + " not supported");
        }

    }

    /**
     * Content encryption algorithm.
     * 
     * @return algorithm Id
     */
    public ASN1ObjectIdentifier getAlgorithmID() {
        return algorithmId;
    }

}
