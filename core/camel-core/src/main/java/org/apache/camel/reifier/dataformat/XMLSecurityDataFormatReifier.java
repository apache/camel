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
package org.apache.camel.reifier.dataformat;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.XMLSecurityDataFormat;
import org.apache.camel.spi.DataFormat;

public class XMLSecurityDataFormatReifier extends DataFormatReifier<XMLSecurityDataFormat> {

    private static final String TRIPLEDES = "http://www.w3.org/2001/04/xmlenc#tripledes-cbc";

    public XMLSecurityDataFormatReifier(DataFormatDefinition definition) {
        super((XMLSecurityDataFormat)definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getSecureTag() != null) {
            setProperty(camelContext, dataFormat, "secureTag", definition.getSecureTag());
        } else {
            setProperty(camelContext, dataFormat, "secureTag", "");
        }

        boolean isSecureTagContents = definition.getSecureTagContents() != null && definition.getSecureTagContents();
        setProperty(camelContext, dataFormat, "secureTagContents", isSecureTagContents);

        if (definition.getPassPhrase() != null || definition.getPassPhraseByte() != null) {
            if (definition.getPassPhraseByte() != null) {
                setProperty(camelContext, dataFormat, "passPhrase", definition.getPassPhraseByte());
            } else {
                setProperty(camelContext, dataFormat, "passPhrase", definition.getPassPhrase().getBytes());
            }
        } else {
            setProperty(camelContext, dataFormat, "passPhrase", "Just another 24 Byte key".getBytes());
        }
        if (definition.getXmlCipherAlgorithm() != null) {
            setProperty(camelContext, dataFormat, "xmlCipherAlgorithm", definition.getXmlCipherAlgorithm());
        } else {
            setProperty(camelContext, dataFormat, "xmlCipherAlgorithm", TRIPLEDES);
        }
        if (definition.getKeyCipherAlgorithm() != null) {
            setProperty(camelContext, dataFormat, "keyCipherAlgorithm", definition.getKeyCipherAlgorithm());
        }
        if (definition.getRecipientKeyAlias() != null) {
            setProperty(camelContext, dataFormat, "recipientKeyAlias", definition.getRecipientKeyAlias());
        }
        if (definition.getKeyOrTrustStoreParametersRef() != null) {
            setProperty(camelContext, dataFormat, "keyOrTrustStoreParametersRef", definition.getKeyOrTrustStoreParametersRef());
        }
        if (definition.getKeyOrTrustStoreParameters() != null) {
            setProperty(camelContext, dataFormat, "keyOrTrustStoreParameters", definition.getKeyOrTrustStoreParameters());
        }
        if (definition.getNamespaces() != null) {
            setProperty(camelContext, dataFormat, "namespaces", definition.getNamespaces());
        }
        if (definition.getKeyPassword() != null) {
            setProperty(camelContext, dataFormat, "keyPassword", definition.getKeyPassword());
        }
        if (definition.getDigestAlgorithm() != null) {
            setProperty(camelContext, dataFormat, "digestAlgorithm", definition.getDigestAlgorithm());
        }
        if (definition.getMgfAlgorithm() != null) {
            setProperty(camelContext, dataFormat, "mgfAlgorithm", definition.getMgfAlgorithm());
        }
        // should be true by default
        boolean isAddKeyValueForEncryptedKey = definition.getAddKeyValueForEncryptedKey() == null || definition.getAddKeyValueForEncryptedKey();
        setProperty(camelContext, dataFormat, "addKeyValueForEncryptedKey", isAddKeyValueForEncryptedKey);
    }

}
