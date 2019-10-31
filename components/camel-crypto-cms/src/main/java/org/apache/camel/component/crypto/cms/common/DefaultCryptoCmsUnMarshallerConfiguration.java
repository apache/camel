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

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class DefaultCryptoCmsUnMarshallerConfiguration extends DefaultCryptoCmsConfiguration implements CryptoCmsUnMarshallerConfiguration {

    @UriParam(label = "decrypt_verify")
    private boolean fromBase64;

    public DefaultCryptoCmsUnMarshallerConfiguration() {
    }

    @Override
    public boolean isFromBase64() {
        return fromBase64;
    }

    /**
     * If <tt>true</tt> then the CMS message is base 64 encoded and must be
     * decoded during the processing. Default value is <code>false</code>.
     */
    public void setFromBase64(boolean base64) {
        this.fromBase64 = base64;
    }
}
