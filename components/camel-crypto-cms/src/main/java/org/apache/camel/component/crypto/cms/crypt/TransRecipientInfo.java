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

import java.security.cert.X509Certificate;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;

/**
 * Information about the receiver of an encrypted message used in
 * CmsEnvelopedDataEncryptor.
 * <p>
 * Represents the "key transport" recipient info alternative: The
 * content-encryption key is encrypted with the public key of the recipient.
 * This technique is compatible to PKCS#7 when creating a RecipientInfo for the
 * public key of the recipient's certificate, identified by issuer and serial
 * number. CMS recommends to use RSA for encrypting the content encryption key.
 */
public interface TransRecipientInfo extends RecipientInfo {

    /** Currently, the key encryption algorithm is fixed to "RSA". */
    String getKeyEncryptionAlgorithm(Exchange exchange) throws CryptoCmsException;

    /**
     * Returns the certificate containign the public key which is used for the
     * encryption and the issuer and serial number which is added to the
     * recipient information.
     */
    X509Certificate getCertificate(Exchange exchange) throws CryptoCmsException;
}
