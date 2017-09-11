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

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.bouncycastle.cms.CMSAttributeTableGenerator;

/**
 * Signer information.
 */
public interface SignerInfo {

    String getSignatureAlgorithm(Exchange exchange) throws CryptoCmsException;

    PrivateKey getPrivateKey(Exchange exchange) throws CryptoCmsException;

    X509Certificate getCertificate(Exchange exchange) throws CryptoCmsException;

    /**
     * Certificates which should be added to the certificate list of the Signed
     * Data instance which belong to the private key. Return an empty array if
     * you do not want that the certificate chain of the private key to be added
     * to the signature certificates.
     */
    Certificate[] getCertificateChain(Exchange exchange) throws CryptoCmsException;

    /**
     * Returns the generator for the signed attributes.
     */
    CMSAttributeTableGenerator getSignedAttributeGenerator(Exchange exchange) throws CryptoCmsException;

    /**
     * Returns the generator for the unsigned attributes. Can be
     * <code>null</code>, then no unsigned attribute is generated.
     */
    CMSAttributeTableGenerator getUnsignedAttributeGenerator(Exchange exchange) throws CryptoCmsException;

}
