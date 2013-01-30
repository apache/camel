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

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class PGPDataFormatTest extends AbstractPGPDataFormatTest {
    
    protected String getKeyFileName() {
        return "org/apache/camel/component/crypto/pubring.gpg";
    }
    
    protected String getKeyFileNameSec() {
        return "org/apache/camel/component/crypto/secring.gpg";
    }
    
    protected String getKeyUserId() {
        return "sdude@nowhere.net";
    }
    
    protected String getKeyPassword() {
        return "sdude";
    }

    @Test
    public void testEncryption() throws Exception {
        doRoundTripEncryptionTests("direct:inline");
    }

    @Test
    public void testEncryption2() throws Exception {
        doRoundTripEncryptionTests("direct:inline2");
    }

    @Test
    public void testEncryptionArmor() throws Exception {
        doRoundTripEncryptionTests("direct:inline-armor");
    }

    @Test
    public void testEncryptionSigned() throws Exception {
        doRoundTripEncryptionTests("direct:inline-sign");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: pgp-format
                // Public Key FileName
                String keyFileName = getKeyFileName();
                // Private Key FileName
                String keyFileNameSec = getKeyFileNameSec();
                // Keyring Userid Used to Encrypt
                String keyUserid = getKeyUserId();
                // Private key password
                String keyPassword = getKeyPassword();

                from("direct:inline")
                        .marshal().pgp(keyFileName, keyUserid)
                        .to("mock:encrypted")
                        .unmarshal().pgp(keyFileNameSec, keyUserid, keyPassword)
                        .to("mock:unencrypted");
                // END SNIPPET: pgp-format

                // START SNIPPET: pgp-format-header
                PGPDataFormat pgpEncrypt = new PGPDataFormat();
                pgpEncrypt.setKeyFileName(keyFileName);
                pgpEncrypt.setKeyUserid(keyUserid);

                PGPDataFormat pgpDecrypt = new PGPDataFormat();
                pgpDecrypt.setKeyFileName(keyFileNameSec);
                pgpDecrypt.setKeyUserid(keyUserid);
                pgpDecrypt.setPassword(keyPassword);

                from("direct:inline2")
                        .marshal(pgpEncrypt)
                        .to("mock:encrypted")
                        .unmarshal(pgpDecrypt)
                        .to("mock:unencrypted");

                from("direct:inline-armor")
                        .marshal().pgp(keyFileName, keyUserid, null, true, true)
                        .to("mock:encrypted")
                        .unmarshal().pgp(keyFileNameSec, keyUserid, keyPassword, true, true)
                        .to("mock:unencrypted");
                // END SNIPPET: pgp-format-header

                // START SNIPPET: pgp-format-signature
                PGPDataFormat pgpSignAndEncrypt = new PGPDataFormat();
                pgpSignAndEncrypt.setKeyFileName(keyFileName);
                pgpSignAndEncrypt.setKeyUserid(keyUserid);
                pgpSignAndEncrypt.setSignatureKeyFileName(keyFileNameSec);
                pgpSignAndEncrypt.setSignatureKeyUserid(keyUserid);
                pgpSignAndEncrypt.setSignaturePassword(keyPassword);

                PGPDataFormat pgpVerifyAndDecrypt = new PGPDataFormat();
                pgpVerifyAndDecrypt.setKeyFileName(keyFileNameSec);
                pgpVerifyAndDecrypt.setKeyUserid(keyUserid);
                pgpVerifyAndDecrypt.setPassword(keyPassword);
                pgpVerifyAndDecrypt.setSignatureKeyFileName(keyFileName);
                pgpVerifyAndDecrypt.setSignatureKeyUserid(keyUserid);

                from("direct:inline-sign")
                        .marshal(pgpSignAndEncrypt)
                        .to("mock:encrypted")
                        .unmarshal(pgpVerifyAndDecrypt)
                        .to("mock:unencrypted");
                // END SNIPPET: pgp-format-signature
            }
        };
    }

}
