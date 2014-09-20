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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;

public class PGPDataFormatDynamicTest extends PGPDataFormatTest {
    // setup a wrong userid
    @Override
    protected String getKeyUserId() {
        return "wrong";
    }

    // setup a wrong userids
    @Override
    protected List<String> getKeyUserIds() {
        List<String> userids = new ArrayList<String>(2);
        userids.add("wrong1");
        userids.add(getKeyUserId());
        return userids;
    }
    
    // setup a wrong signature userids
    @Override
    protected List<String> getSignatureKeyUserIds() {
        List<String> userids = new ArrayList<String>(2);
        userids.add("wrong1");
        userids.add(getKeyUserId());
        return userids;
    }

    // setup a wrong password
    @Override
    protected String getKeyPassword() {
        return "wrong";
    }

    //setup wrong algorithm
    @Override
    protected int getAlgorithm() {
        return -5;
    }

    //setup wrong hash algorithm
    @Override
    protected int getHashAlgorithm() {
        return -5;
    }

    //setup wrong compression algorithm
    @Override
    protected int getCompressionAlgorithm() {
        return -5;
    }

    // override wrong userid and password with correct userid and password in the headers
    protected Map<String, Object> getHeaders() {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(PGPKeyAccessDataFormat.KEY_USERID, "sdude@nowhere.net");
        headers.put(PGPKeyAccessDataFormat.KEY_USERIDS, Collections.singletonList("second"));
        headers.put(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERID, "sdude@nowhere.net");
        headers.put(PGPDataFormat.KEY_PASSWORD, "sdude");
        headers.put(PGPDataFormat.SIGNATURE_KEY_PASSWORD, "sdude");
        headers.put(PGPKeyAccessDataFormat.ENCRYPTION_ALGORITHM, SymmetricKeyAlgorithmTags.AES_128);
        headers.put(PGPKeyAccessDataFormat.SIGNATURE_HASH_ALGORITHM, HashAlgorithmTags.SHA512);
        headers.put(PGPKeyAccessDataFormat.COMPRESSION_ALGORITHM, CompressionAlgorithmTags.ZLIB);
        headers.put(PGPKeyAccessDataFormat.SIGNATURE_KEY_USERIDS, Collections.singletonList("second"));
        return headers;
    }
}
