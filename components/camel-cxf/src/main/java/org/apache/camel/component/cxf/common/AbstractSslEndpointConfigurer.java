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
package org.apache.camel.component.cxf.common;

import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.net.ssl.SSLSocketFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.transport.http.HTTPConduit;

public class AbstractSslEndpointConfigurer extends AbstractTLSClientParameterConfigurer {
    protected final SSLContextParameters sslContextParameters;
    protected final CamelContext camelContext;

    public AbstractSslEndpointConfigurer(SSLContextParameters sslContextParameters, CamelContext camelContext) {
        this.sslContextParameters = sslContextParameters;
        this.camelContext = camelContext;
    }

    protected void setupHttpConduit(HTTPConduit httpConduit) {
        TLSClientParameters tlsClientParameters = tryToGetTLSClientParametersFromConduit(httpConduit);
        tlsClientParameters.setSSLSocketFactory(tryToGetSSLSocketFactory());
        httpConduit.setTlsClientParameters(tlsClientParameters);
    }

    private SSLSocketFactory tryToGetSSLSocketFactory() {
        try {
            return sslContextParameters.createSSLContext(camelContext)
                    .getSocketFactory();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Setting SSL failed", e);
        }
    }
}
