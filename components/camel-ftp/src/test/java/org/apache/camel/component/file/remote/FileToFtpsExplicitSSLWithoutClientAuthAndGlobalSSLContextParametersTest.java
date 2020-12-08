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

import org.apache.camel.CamelContext;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

public class FileToFtpsExplicitSSLWithoutClientAuthAndGlobalSSLContextParametersTest
        extends FileToFtpsExplicitSSLWithoutClientAuthTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("server.jks");
        ksp.setPassword("password");

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setSecureSocketProtocol("SSLv3");
        sslContextParameters.setTrustManagers(tmp);
        context.setSSLContextParameters(sslContextParameters);

        ((SSLContextParametersAware) context.getComponent("ftps")).setUseGlobalSslContextParameters(true);
        return context;
    }

    @Override
    protected String getFtpUrl() {
        return "ftps://admin@localhost:{{ftp.server.port}}"
               + "/tmp2/camel?password=admin&initialDelay=2000&disableSecureDataChannelDefaults=true"
               + "&implicit=false&delete=true";
    }
}
