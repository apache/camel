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

import java.util.Collection;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.common.CryptoCmsUnMarshallerConfiguration;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;

public interface EnvelopedDataDecryptorConfiguration extends CryptoCmsUnMarshallerConfiguration {

    /**
     * Returns the private keys with their public keys in the X.509 certificate
     * which can be used for the decryption. The certificate is used for finding
     * the corresponding Key Transport Recipient Info in the Enveloped Data
     * object.
     */
    Collection<PrivateKeyWithCertificate> getPrivateKeyCertificateCollection(Exchange exchange) throws CryptoCmsException;

    /** Creates a copy of the current instance, for example by cloning. */
    EnvelopedDataDecryptorConfiguration copy();

}
