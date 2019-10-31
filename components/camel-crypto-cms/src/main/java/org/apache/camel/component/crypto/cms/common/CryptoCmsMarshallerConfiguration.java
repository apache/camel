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
package org.apache.camel.component.crypto.cms.common;

import org.apache.camel.CamelContext;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.slf4j.Logger;

@UriParams
public abstract class CryptoCmsMarshallerConfiguration {

    private final CamelContext context;

    @UriParam(label = "encrypt_sign", defaultValue = "false")
    private Boolean toBase64 = Boolean.FALSE;

    public CryptoCmsMarshallerConfiguration(CamelContext context) {
        this.context = context;
    }

    public Boolean getToBase64() {
        return toBase64;
    }

    /**
     * Indicates whether the Signed Data or Enveloped Data instance shall be
     * base 64 encoded. Default value is <code>false</code>.
     */
    public void setToBase64(Boolean toBase64) {
        this.toBase64 = toBase64;
    }

    protected CamelContext getContext() {
        return context;
    }

    protected void logErrorAndThrow(final Logger log, String message) throws CryptoCmsException {
        log.error(message);
        throw new CryptoCmsException(message);
    }

}
