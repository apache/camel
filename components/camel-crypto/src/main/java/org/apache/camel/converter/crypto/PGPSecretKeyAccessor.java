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
package org.apache.camel.converter.crypto;

import java.util.List;

import org.apache.camel.Exchange;
import org.bouncycastle.openpgp.PGPPrivateKey;

public interface PGPSecretKeyAccessor {

    /**
     * Returns the signer keys for the given user ID parts. This method is used
     * for signing.
     * 
     * @param exchange
     *            exchange, can be <code>null</code>
     * @param useridParts
     *            parts of User IDs, can be <code>null</code> or empty, then an
     *            empty list must be returned
     * @return list of secret keys with their private keys and User Ids which
     *         corresponds to one of the useridParts, must not be
     *         <code>null</code>, can be empty
     */
    List<PGPSecretKeyAndPrivateKeyAndUserId> getSignerKeys(Exchange exchange, List<String> useridParts) throws Exception;

    /**
     * Returns the private key with a certain key ID. This method is used for
     * decrypting.
     * 
     * @param exchange
     *            exchange, can be <code>null</code>
     * 
     * @param keyId
     *            key ID
     * @return private key or <code>null</code> if the key cannot be found
     */
    PGPPrivateKey getPrivateKey(Exchange exchange, long keyId) throws Exception;


}
