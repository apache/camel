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

import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.CryptoDataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public class CryptoDataFormatReifier extends DataFormatReifier<CryptoDataFormat> {

    public CryptoDataFormatReifier(DataFormatDefinition definition) {
        super((CryptoDataFormat) definition);
    }

    @Override
    protected DataFormat doCreateDataFormat(CamelContext camelContext) {
        DataFormat cryptoFormat = super.doCreateDataFormat(camelContext);

        if (ObjectHelper.isNotEmpty(definition.getKeyRef())) {
            Key key = CamelContextHelper.mandatoryLookup(camelContext, definition.getKeyRef(), Key.class);
            setProperty(camelContext, cryptoFormat, "key", key);
        }
        if (ObjectHelper.isNotEmpty(definition.getAlgorithmParameterRef())) {
            AlgorithmParameterSpec spec = CamelContextHelper.mandatoryLookup(camelContext,
                    definition.getAlgorithmParameterRef(), AlgorithmParameterSpec.class);
            setProperty(camelContext, cryptoFormat, "AlgorithmParameterSpec", spec);
        }
        if (ObjectHelper.isNotEmpty(definition.getInitVectorRef())) {
            byte[] iv = CamelContextHelper.mandatoryLookup(camelContext, definition.getInitVectorRef(), byte[].class);
            setProperty(camelContext, cryptoFormat, "InitializationVector", iv);
        }
        return cryptoFormat;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        Boolean answer = ObjectHelper.toBoolean(definition.getShouldAppendHMAC());
        if (answer != null && !answer) {
            setProperty(camelContext, dataFormat, "shouldAppendHMAC", Boolean.FALSE);
        } else {
            setProperty(camelContext, dataFormat, "shouldAppendHMAC", Boolean.TRUE);
        }
        answer = ObjectHelper.toBoolean(definition.getInline());
        if (answer != null && answer) {
            setProperty(camelContext, dataFormat, "shouldInlineInitializationVector", Boolean.TRUE);
        } else {
            setProperty(camelContext, dataFormat, "shouldInlineInitializationVector", Boolean.FALSE);
        }
        if (definition.getAlgorithm() != null) {
            setProperty(camelContext, dataFormat, "algorithm", definition.getAlgorithm());
        }
        if (definition.getCryptoProvider() != null) {
            setProperty(camelContext, dataFormat, "cryptoProvider", definition.getCryptoProvider());
        }
        if (definition.getMacAlgorithm() != null) {
            setProperty(camelContext, dataFormat, "macAlgorithm", definition.getMacAlgorithm());
        }
        if (definition.getBuffersize() != null) {
            setProperty(camelContext, dataFormat, "buffersize", definition.getBuffersize());
        }
    }


}
