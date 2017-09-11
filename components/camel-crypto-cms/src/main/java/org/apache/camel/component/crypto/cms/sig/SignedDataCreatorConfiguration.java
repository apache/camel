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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.component.crypto.cms.common.CryptoCmsMarshallerConfiguration;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class SignedDataCreatorConfiguration extends CryptoCmsMarshallerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SignedDataCreatorConfiguration.class);

    @UriParam(label = "sign", defaultValue = "true")
    private Boolean includeContent = Boolean.TRUE;

    @UriParam(label = "sign", multiValue = true, description = "Signer information: reference to a bean which implements org.apache.camel.component.crypto.cms.api.SignerInfo")
    private final List<SignerInfo> signer = new ArrayList<SignerInfo>(3);

    public SignedDataCreatorConfiguration(CamelContext context) {
        super(context);
    }

    public Boolean getIncludeContent() {
        return includeContent;
    }

    /**
     * Indicates whether the signed content should be included into the Signed
     * Data instance. If false then a detached Signed Data instance is created
     * in the header CamelCryptoCmsSignedData.
     */
    public void setIncludeContent(Boolean includeContent) {
        this.includeContent = includeContent;
    }

    public List<SignerInfo> getSigner() {
        return signer;
    }

    public void setSigner(SignerInfo signer) {
        this.signer.add(signer);
    }

    // for multi values
    public void setSigner(List<?> signers) {
        if (signers == null) {
            return;
        }
        for (Object signerOb : signers) {
            if (signerOb instanceof String) {
                String signerName = (String)signerOb;
                String valueNoHash = signerName.replaceAll("#", "");
                if (getContext() != null && signerName != null) {
                    SignerInfo signer = getContext().getRegistry().lookupByNameAndType(valueNoHash, SignerInfo.class);
                    if (signer != null) {
                        setSigner(signer);
                    }
                }
            }
        }

    }

    public void init() throws CryptoCmsException {

        if (signer.isEmpty()) {
            logErrorAndThrow(LOG, "No signer set.");
        }

    }

}
