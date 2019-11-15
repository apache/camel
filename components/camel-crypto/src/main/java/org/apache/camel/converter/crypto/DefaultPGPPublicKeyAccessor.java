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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

/**
 * Caches a public key ring.
 */
public class DefaultPGPPublicKeyAccessor implements PGPPublicKeyAccessor {
    

    private final PGPPublicKeyRingCollection pgpPublicKeyRing;

    public DefaultPGPPublicKeyAccessor(byte[] publicKeyRing) throws IOException, PGPException {
        ObjectHelper.notNull(publicKeyRing, "publicKeyRing");
        pgpPublicKeyRing = 
            new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(publicKeyRing)),
                                           new BcKeyFingerprintCalculator());
    }

    @Override
    public List<PGPPublicKey> getEncryptionKeys(Exchange exchange, List<String> useridParts) throws Exception {
        return PGPDataFormatUtil.findPublicKeys(useridParts, true, pgpPublicKeyRing);
    }

    @Override
    public PGPPublicKey getPublicKey(Exchange exchange, long keyId, List<String> userIdParts) throws Exception {       
        return PGPDataFormatUtil.getPublicKeyWithKeyIdAndUserID(keyId, userIdParts, pgpPublicKeyRing);
    }

}
