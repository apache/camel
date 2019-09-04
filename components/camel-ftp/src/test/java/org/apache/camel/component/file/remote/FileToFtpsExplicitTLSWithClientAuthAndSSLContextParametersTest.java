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
package org.apache.camel.component.file.remote;

import org.apache.camel.BindToRegistry;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

public class FileToFtpsExplicitTLSWithClientAuthAndSSLContextParametersTest extends FileToFtpsExplicitTLSWithClientAuthTest {

    @BindToRegistry("sslContextParameters")
    public SSLContextParameters createSslContextParams() throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("server.jks");
        ksp.setPassword("password");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("password");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setSecureSocketProtocol("TLSv1.2");
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }

    @Override
    protected String getFtpUrl() {
        return "ftps://admin@localhost:" + getPort() + "/tmp2/camel?password=admin&initialDelay=2000&disableSecureDataChannelDefaults=true"
               + "&implicit=false&sslContextParameters=#sslContextParameters&delete=true";
    }
}
