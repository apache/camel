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
package org.apache.camel.component.file.remote;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;

public class FileToFtpsExplicitTLSWithoutClientAuthAndSSLContextParametersTest extends FileToFtpsExplicitTLSWithoutClientAuthTest {
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("server.jks");
        ksp.setPassword("password");
        
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);
        
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setSecureSocketProtocol("TLS");
        sslContextParameters.setTrustManagers(tmp);
        
        JndiRegistry registry = super.createRegistry();
        registry.bind("sslContextParameters", sslContextParameters);
        return registry;
    }
    
    protected String getFtpUrl() {
        return "ftps://admin@localhost:" + getPort() + "/tmp2/camel?password=admin&consumer.initialDelay=2000&disableSecureDataChannelDefaults=true"
                + "&isImplicit=false&sslContextParameters=#sslContextParameters&delete=true";
    }
}
