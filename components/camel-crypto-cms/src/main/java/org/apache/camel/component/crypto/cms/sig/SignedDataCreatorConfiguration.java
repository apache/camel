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

    @UriParam(label = "sign", javaType = "java.lang.String", 
              description = "Signer information: reference to bean(s) which implements org.apache.camel.component.crypto.cms.api.SignerInfo. Multiple values can be separated by comma")
    private List<SignerInfo> signer = new ArrayList<>();

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

    public void setSigner(List<SignerInfo> signer) {
        this.signer = signer;
    }

    public void setSigner(String signer) {
        String[] values = signer.split(",");
        for (String s : values) {
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            if (getContext() != null) {
                SignerInfo obj = getContext().getRegistry().lookupByNameAndType(s, SignerInfo.class);
                if (obj != null) {
                    addSigner(obj);
                }
            }
        }
    }

    public void addSigner(SignerInfo info) {
        if (this.signer == null) {
            this.signer = new ArrayList<>();
        }
        this.signer.add(info);
    }

    public void init() throws CryptoCmsException {

        if (signer.isEmpty()) {
            logErrorAndThrow(LOG, "No signer set.");
        }

    }

}
