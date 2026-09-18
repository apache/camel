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
package org.apache.camel.component.as2.api.protocol;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A single {@link ResponseMDN} is registered on the shared {@code HttpProcessor} and serves every request, so the
 * security material resolved for one request must never be written back onto the instance. A deployment hosting more
 * than one partner on different paths, each with its own keys, would otherwise be able to sign one partner's MDN with
 * another partner's private key.
 * <p/>
 * The fields are {@code final}, which makes that structurally impossible; this test states the property directly so the
 * intent survives a refactor that changes how the material is carried.
 */
class ResponseMDNPerRequestKeysTest {

    @Test
    void perRequestKeysAreNotStoredOnTheSharedInstance() throws Exception {
        // the three-arg constructor is the dynamic-keys form: material comes from the context per request
        ResponseMDN responseMDN = new ResponseMDN("1.1", "camel.apache.org", null);

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        HttpCoreContext context = HttpCoreContext.create();
        context.setAttribute(AS2ServerConnection.AS2_SIGNING_ALGORITHM, AS2SignatureAlgorithm.SHA256WITHRSA);
        context.setAttribute(AS2ServerConnection.AS2_SIGNING_PRIVATE_KEY, keyPair.getPrivate());
        context.setAttribute(AS2ServerConnection.AS2_DECRYPTING_PRIVATE_KEY, keyPair.getPrivate());

        // 2xx so processing continues past the status check; no request on the context so it returns
        // straight after the material has been resolved, which is the point under test
        HttpResponse response = new BasicHttpResponse(200);
        responseMDN.process(response, null, context);

        assertThat(fieldValue(responseMDN, "signingAlgorithm")).isNull();
        assertThat(fieldValue(responseMDN, "signingPrivateKey")).isNull();
        assertThat(fieldValue(responseMDN, "signingCertificateChain")).isNull();
        assertThat(fieldValue(responseMDN, "decryptingPrivateKey")).isNull();
        assertThat(fieldValue(responseMDN, "validateSigningCertificateChain")).isNull();
    }

    private static Object fieldValue(ResponseMDN target, String name) throws Exception {
        Field field = ResponseMDN.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
