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

import java.security.cert.X509Certificate;
import java.util.Collection;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.common.CryptoCmsConstants;
import org.apache.camel.component.crypto.cms.common.CryptoCmsUnMarshallerConfiguration;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;

public interface SignedDataVerifierConfiguration extends CryptoCmsUnMarshallerConfiguration {

    /**
     * Indicates whether the value in the Signed Data header (given by
     * {@link CryptoCmsConstants#CAMEL_CRYPTO_CMS_SIGNED_DATA} is base64
     * encoded.
     */
    Boolean isSignedDataHeaderBase64(Exchange exchange) throws CryptoCmsException;

    /**
     * If <code>true</code> then the signatures of all signers are checked. If
     * <code>false</code> then the verifier searches for a signer which matches
     * with one of the specified certificates and verifies only the signature of
     * the first found signer.
     */
    Boolean isVerifySignaturesOfAllSigners(Exchange exchange) throws CryptoCmsException;

    /**
     * Returns the collection of certificates whose public keys are used to
     * verify the signatures contained in the Signed Data object if the
     * certificates match the signer information given in the Signed Data
     * object.
     */
    Collection<X509Certificate> getCertificates(Exchange exchange) throws CryptoCmsException;

    /** Creates a copy of this instance. For example by cloning. */
    SignedDataVerifierConfiguration copy();

}
