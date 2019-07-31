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
import org.apache.camel.model.dataformat.PGPDataFormat;
import org.apache.camel.spi.DataFormat;

public class PGPDataFormatReifier extends DataFormatReifier<PGPDataFormat> {

    public PGPDataFormatReifier(DataFormatDefinition definition) {
        super((PGPDataFormat) definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getKeyUserid() != null) {
            setProperty(camelContext, dataFormat, "keyUserid", definition.getKeyUserid());
        }
        if (definition.getSignatureKeyUserid() != null) {
            setProperty(camelContext, dataFormat, "signatureKeyUserid", definition.getSignatureKeyUserid());
        }
        if (definition.getPassword() != null) {
            setProperty(camelContext, dataFormat, "password", definition.getPassword());
        }
        if (definition.getSignaturePassword() != null) {
            setProperty(camelContext, dataFormat, "signaturePassword", definition.getSignaturePassword());
        }
        if (definition.getKeyFileName() != null) {
            setProperty(camelContext, dataFormat, "keyFileName", definition.getKeyFileName());
        }
        if (definition.getSignatureKeyFileName() != null) {
            setProperty(camelContext, dataFormat, "signatureKeyFileName", definition.getSignatureKeyFileName());
        }
        if (definition.getSignatureKeyRing() != null) {
            setProperty(camelContext, dataFormat, "signatureKeyRing", definition.getSignatureKeyRing());
        }
        if (definition.getArmored() != null) {
            setProperty(camelContext, dataFormat, "armored", definition.getArmored());
        }
        if (definition.getIntegrity() != null) {
            setProperty(camelContext, dataFormat, "integrity", definition.getIntegrity());
        }
        if (definition.getProvider() != null) {
            setProperty(camelContext, dataFormat, "provider", definition.getProvider());
        }
        if (definition.getAlgorithm() != null) {
            setProperty(camelContext, dataFormat, "algorithm", definition.getAlgorithm());
        }
        if (definition.getCompressionAlgorithm() != null) {
            setProperty(camelContext, dataFormat, "compressionAlgorithm", definition.getCompressionAlgorithm());
        }
        if (definition.getHashAlgorithm() != null) {
            setProperty(camelContext, dataFormat, "hashAlgorithm", definition.getHashAlgorithm());
        }
        if (definition.getSignatureVerificationOption() != null) {
            setProperty(camelContext, dataFormat, "signatureVerificationOption", definition.getSignatureVerificationOption());
        }
    }

}
