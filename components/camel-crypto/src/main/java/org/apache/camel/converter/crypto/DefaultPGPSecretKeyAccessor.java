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
package org.apache.camel.converter.crypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

/**
 * Caches a Secret Keyring. Assumes that the password for all private keys is
 * the same.
 * 
 */
public class DefaultPGPSecretKeyAccessor implements PGPSecretKeyAccessor {

    private final Map<String, List<PGPSecretKeyAndPrivateKeyAndUserId>> userIdPart2SecretKeyList = new HashMap<String, List<PGPSecretKeyAndPrivateKeyAndUserId>>(
            3);

    private final Map<Long, PGPPrivateKey> keyId2PrivateKey = new HashMap<Long, PGPPrivateKey>(3);

    private final PGPSecretKeyRingCollection pgpSecretKeyring;

    private final String password;

    private final String provider;

    /**
     * 
     * @param secretKeyRing
     *            secret key ring as byte array
     * @param password
     *            password for the private keys, assuming that all private keys
     *            have the same password
     * @param provider
     * @throws PGPException
     * @throws IOException
     */
    public DefaultPGPSecretKeyAccessor(byte[] secretKeyRing, String password, String provider) throws PGPException, IOException {
        ObjectHelper.notNull(secretKeyRing, "secretKeyRing");
        ObjectHelper.notEmpty(password, "password");
        ObjectHelper.notEmpty(provider, "provider");
        pgpSecretKeyring = 
            new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(secretKeyRing)),
                                           new BcKeyFingerprintCalculator());
        this.password = password;
        this.provider = provider;
    }

    @Override
    public List<PGPSecretKeyAndPrivateKeyAndUserId> getSignerKeys(Exchange exchange, List<String> useridParts) throws Exception {
        List<PGPSecretKeyAndPrivateKeyAndUserId> result = new ArrayList<PGPSecretKeyAndPrivateKeyAndUserId>(3);
        for (String useridPart : useridParts) {
            List<PGPSecretKeyAndPrivateKeyAndUserId> partResult = userIdPart2SecretKeyList.get(useridPart);
            if (partResult == null) {
                partResult = PGPDataFormatUtil.findSecretKeysWithPrivateKeyAndUserId(Collections.singletonMap(useridPart, password),
                        provider, pgpSecretKeyring);
                userIdPart2SecretKeyList.put(useridPart, partResult);
            }
            result.addAll(partResult);
        }
        return result;
    }

    @Override
    public PGPPrivateKey getPrivateKey(Exchange exchange, long keyId) throws Exception {
        Long keyIdLong = Long.valueOf(keyId);
        PGPPrivateKey result = keyId2PrivateKey.get(keyIdLong);
        if (result == null) {
            result = PGPDataFormatUtil.findPrivateKeyWithkeyId(keyId, password, null, provider, pgpSecretKeyring);
            if (result != null) {
                keyId2PrivateKey.put(keyIdLong, result);
            }
        }
        return result;
    }

}
