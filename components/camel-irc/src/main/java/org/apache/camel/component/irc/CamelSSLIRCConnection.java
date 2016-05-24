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
package org.apache.camel.component.irc;

import java.io.IOException;
import java.net.SocketException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.schwering.irc.lib.ssl.SSLIRCConnection;
import org.schwering.irc.lib.ssl.SSLNotSupportedException;

/**
 * Customized version of {@link SSLIRCConnection} used to support the use of an {@link SSLContextParameters} instance
 * for JSSE configuration.
 */
public class CamelSSLIRCConnection extends SSLIRCConnection {
    
    private SSLContextParameters sslContextParameters;
    private CamelContext camelContext;

    public CamelSSLIRCConnection(String host, int portMin, int portMax, String pass, 
                                 String nick, String username, String realname,
                                 SSLContextParameters sslContextParameters) {
        super(host, portMin, portMax, pass, nick, username, realname);
        this.sslContextParameters = sslContextParameters;
    }

    public CamelSSLIRCConnection(String host, int[] ports, String pass,
                                 String nick, String username, String realname,
                                 SSLContextParameters sslContextParameters, CamelContext camelContext) {
        super(host, ports, pass, nick, username, realname);
        this.sslContextParameters = sslContextParameters;
        this.camelContext = camelContext;
    }

    @Override
    public void connect() throws IOException {
        
        if (sslContextParameters == null) {
            super.connect();
        } else {
            if (level != 0) {
                throw new SocketException("Socket closed or already open (" + level + ")");
            }
            
            IOException exception = null;
            
            final SSLContext sslContext;
            try {
                sslContext = sslContextParameters.createSSLContext(camelContext);
            } catch (GeneralSecurityException e) {
                throw new RuntimeCamelException("Error in SSLContextParameters configuration or instantiation.", e);
            }
            
            final SSLSocketFactory sf = sslContext.getSocketFactory();
            
            SSLSocket s = null;
            
            for (int i = 0; i < ports.length && s == null; i++) {
                try {
                    s = (SSLSocket)sf.createSocket(host, ports[i]);
                    s.startHandshake();
                    exception = null;
                } catch (SSLNotSupportedException exc) {
                    if (s != null) {
                        s.close();
                    }
                    s = null;
                    throw exc;
                } catch (IOException exc) {
                    if (s != null) {
                        s.close();
                    }
                    s = null;
                    exception = exc; 
                }
            }
            if (exception != null) {
                throw exception; // connection wasn't successful at any port
            }
            
            prepare(s);
        }        
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }
}
