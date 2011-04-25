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
package org.apache.camel.util.jsse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents {@link SSLContext} configuration options used in instantiating an
 * {@code SSLContext} instance.
 */
public class SSLContextParameters extends BaseSSLContextParameters {
    
    protected static final String DEFAULT_SECURE_SOCKET_PROTOCOL = "TLS";
    
    private static final Logger LOG = LoggerFactory.getLogger(SSLContextParameters.class);

    /**
     * The optional key manager configuration for creating the
     * {@link KeyManager}s used in constructing an {@link SSLContext}.
     */
    private KeyManagersParameters keyManagers;
    
    /**
     * The optional trust manager configuration for creating the
     * {@link TrustManager}s used in constructing an {@link SSLContext}.
     */
    private TrustManagersParameters trustManagers;
        
    /**
     * The optional secure random configuration options to use for constructing
     * the {@link SecureRandom} used in the creation of an {@link SSLContext].
     */
    private SecureRandomParameters secureRandom;
    
    /**
     * The optional configuration options to be applied purely to the client side settings
     * of the {@link SSLContext}.  Settings specified here override any duplicate settings
     * provided at the overall level by this class.  These parameters apply to 
     * {@link SSLSocketFactory}s and {@link SSLEngine}s produced by the the {@code SSLContext}
     * produced from this class as well as to the {@code SSLContext} itself.
     */
    private SSLContextClientParameters clientParameters;
    
    /**
     * The optional configuration options to be applied purely to the server side settings
     * of the {@link SSLContext}.  Settings specified here override any duplicate settings
     * provided at the overall level by this class.  These parameters apply to 
     * {@link SSLServerSocketFactory}s and {@link SSLEngine}s produced by the the {@code SSLContext}
     * produced from this class as well as to the {@code SSLContext} itself.
     */
    private SSLContextServerParameters serverParameters;

    /**
     * The optional provider identifier for the JSSE implementation to use when
     * constructing an {@link SSLContext}.
     */
    private String provider;

    /**
     * The optional protocol for the secure sockets created by the {@link SSLContext}
     * represented by this instance's configuration. See Appendix A in the <a
     * href="http://download.oracle.com/javase/6/docs/technotes/guides//security/jsse/JSSERefGuide.html#AppA"
     * >Java Secure Socket Extension Reference Guide</a> for information about
     * standard protocol names.
     */
    private String secureSocketProtocol;    

    public KeyManagersParameters getKeyManagers() {
        return keyManagers;
    }

    /**
     * Sets the optional key manager configuration for creating the
     * {@link KeyManager}s used in constructing an {@link SSLContext}.
     * 
     * @param keyManagers the options or {@code null} to provide no
     *            {@code KeyManager}s
     */
    public void setKeyManagers(KeyManagersParameters keyManagers) {
        this.keyManagers = keyManagers;
    }

    public TrustManagersParameters getTrustManagers() {
        return trustManagers;
    }

    /**
     * Sets the optional trust manager configuration for creating the
     * {@link TrustManager}s used in constructing an {@link SSLContext}.
     * 
     * @param trustManagers the options or {@code null} to provide no
     *            {@code TrustManager}s
     */
    public void setTrustManagers(TrustManagersParameters trustManagers) {
        this.trustManagers = trustManagers;
    }

    public SecureRandomParameters getSecureRandom() {
        return secureRandom;
    }

    /**
     * Sets the optional secure random configuration options to use for 
     * constructing the {@link SecureRandom} used in the creation of an {@link SSLContext].
     *
     * @param secureRandom the options or {@code null} to use the default
     */
    public void setSecureRandom(SecureRandomParameters secureRandom) {
        this.secureRandom = secureRandom;
    }
    
    public SSLContextClientParameters getClientParameters() {
        return clientParameters;
    }

    /**
     * The optional configuration options to be applied purely to the client side settings
     * of the {@link SSLContext}.  Settings specified here override any duplicate settings
     * provided at the overall level by this class.  These parameters apply to 
     * {@link SSLSocketFactory}s and {@link SSLEngine}s produced by the the {@code SSLContext}
     * produced from this class as well as to the {@code SSLContext} itself.
     *
     * @param clientParameters the optional additional client-side parameters
     */
    public void setClientParameters(SSLContextClientParameters clientParameters) {
        this.clientParameters = clientParameters;
    }

    public SSLContextServerParameters getServerParameters() {
        return serverParameters;
    }

    /**
     * The optional configuration options to be applied purely to the server side settings
     * of the {@link SSLContext}.  Settings specified here override any duplicate settings
     * provided at the overall level by this class.  These parameters apply to 
     * {@link SSLServerSocketFactory}s and {@link SSLEngine}s produced by the the {@code SSLContext}
     * produced from this class as well as to the {@code SSLContext} itself.
     *
     * @param serverParameters the optional additional client-side parameters
     */
    public void setServerParameters(SSLContextServerParameters serverParameters) {
        this.serverParameters = serverParameters;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Sets the optional provider identifier to use when constructing an
     * {@link SSLContext}.
     * 
     * @param provider the identifier (from the list of available providers
     *            returned by {@link Security#getProviders()}) or {@code null}
     *            to use the highest priority provider implementing the secure
     *            socket protocol
     *
     * @see Security#getProviders(java.util.Map)
     * @see #setSecureSocketProtocol(String)            
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSecureSocketProtocol() {
        if (this.secureSocketProtocol == null) {
            return DEFAULT_SECURE_SOCKET_PROTOCOL;
        }
        return this.secureSocketProtocol;
    }

    /**
     * Sets the optional protocol for the secure sockets created by the
     * {@link SSLContext} represented by this instance's configuration. Defaults
     * to TLS. See Appendix A in the <a href=
     * "http://download.oracle.com/javase/6/docs/technotes/guides//security/jsse/JSSERefGuide.html#AppA"
     * >Java Secure Socket Extension Reference Guide</a> for information about
     * standard protocol names.
     * 
     * @param secureSocketProtocol the name of the protocol or {@code null} to
     *            use the default (TLS)
     */
    public void setSecureSocketProtocol(String secureSocketProtocol) {
        this.secureSocketProtocol = secureSocketProtocol;
    }
    
    ////////////////////////////////////////////
    
    /**
     * Creates an {@link SSLContext} based on the related configuration options
     * of this instance. Namely, {@link #keyManagers}, {@link #trustManagers}, and
     * {@link #secureRandom}, but also respecting the chosen provider and secure
     * socket protocol as well.
     * 
     * @return a newly configured instance
     *
     * @throws GeneralSecurityException if there is a problem in this instances
     *             configuration or that of its nested configuration options
     * @throws IOException if there is an error reading a key/trust store
     */
    public SSLContext createSSLContext() throws GeneralSecurityException, IOException {
        
        LOG.debug("Creating SSLContext from SSLContextParameters: {}", this);

        KeyManager[] keyManagers = this.keyManagers == null ? null : this.keyManagers.createKeyManagers();
        TrustManager[] trustManagers = this.trustManagers == null ? null : this.trustManagers.createTrustManagers();
        SecureRandom secureRandom = this.secureRandom == null ? null : this.secureRandom.createSecureRandom();

        SSLContext context;
        if (this.getProvider() == null) {
            context = SSLContext.getInstance(this.getSecureSocketProtocol());
        } else {
            context = SSLContext.getInstance(this.getSecureSocketProtocol(), this.getProvider());
        }
        
        context.init(keyManagers, trustManagers, secureRandom);
        
        this.configureSSLContext(context);
        
        // Decorate the context.
        context = new SSLContextDecorator(
                new SSLContextSpiDecorator(
                        context,
                        this.getSSLEngineConfigurers(context),
                        this.getSSLSocketFactoryConfigurers(context),
                        this.getSSLServerSocketFactoryConfigurers(context)));
        
        return context;
    }
    
    @Override
    protected void configureSSLContext(SSLContext context) throws GeneralSecurityException {
        LOG.trace("Configuring client and server side SSLContext parameters...");
        super.configureSSLContext(context);
        
        if (this.getClientParameters() != null) {
            LOG.debug("Overriding client-side SSLContext parameters with configured client parameters.");
            this.getClientParameters().configureSSLContext(context);
        }

        if (this.getServerParameters() != null) {
            LOG.debug("Overriding server-side SSLContext parameters with configured server parameters.");
            this.getServerParameters().configureSSLContext(context);
        }        
        
        LOG.trace("Configured client and server side SSLContext parameters.");
    }
    
    @Override
    protected List<Configurer<SSLEngine>> getSSLEngineConfigurers(SSLContext context) {
        LOG.trace("Collecting client and server side SSLEngine configurers...");
        List<Configurer<SSLEngine>> configurers = super.getSSLEngineConfigurers(context);
        
        if (this.getClientParameters() != null) {
            LOG.debug("Augmenting SSLEngine configurers with configurers from client parameters.");
            configurers.addAll(this.getClientParameters().getSSLEngineConfigurers(context));
        }
        
        if (this.getServerParameters() != null) {
            LOG.debug("Augmenting SSLEngine configurers with configurers from server parameters.");
            configurers.addAll(this.getServerParameters().getSSLEngineConfigurers(context));
        }
        
        LOG.trace("Collected client and server side SSLEngine configurers.");
        
        return configurers;
    }
    
    @Override
    protected List<Configurer<SSLSocketFactory>> getSSLSocketFactoryConfigurers(SSLContext context) {
        LOG.trace("Collecting SSLSocketFactory configurers...");
        List<Configurer<SSLSocketFactory>> configurers = super.getSSLSocketFactoryConfigurers(context);
        
        if (this.getClientParameters() != null) {
            LOG.debug("Augmenting SSLSocketFactory configurers with configurers from client parameters.");
            configurers.addAll(this.getClientParameters().getSSLSocketFactoryConfigurers(context));
        }
        
        LOG.trace("Collected SSLSocketFactory configurers.");
        
        return configurers;
    }

    @Override
    protected List<Configurer<SSLServerSocketFactory>> getSSLServerSocketFactoryConfigurers(SSLContext context) {
        LOG.trace("Collecting SSLServerSocketFactory configurers...");
        List<Configurer<SSLServerSocketFactory>> configurers = super.getSSLServerSocketFactoryConfigurers(context);
        
        if (this.getServerParameters() != null) {
            LOG.debug("Augmenting SSLServerSocketFactory configurers with configurers from server parameters.");
            configurers.addAll(this.getServerParameters().getSSLServerSocketFactoryConfigurers(context));
        }
        
        LOG.trace("Collected client and server side SSLServerSocketFactory configurers.");
        
        return configurers;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SSLContextParameters [keyManagers=");
        builder.append(keyManagers);
        builder.append(", trustManagers=");
        builder.append(trustManagers);
        builder.append(", secureRandom=");
        builder.append(secureRandom);
        builder.append(", clientParameters=");
        builder.append(clientParameters);
        builder.append(", serverParameters=");
        builder.append(serverParameters);
        builder.append(", provider=");
        builder.append(provider);
        builder.append(", secureSocketProtocol=");
        builder.append(secureSocketProtocol);
        builder.append(", getCipherSuites()=");
        builder.append(getCipherSuites());
        builder.append(", getCipherSuitesFilter()=");
        builder.append(getCipherSuitesFilter());
        builder.append(", getSecureSocketProtocols()=");
        builder.append(getSecureSocketProtocols());
        builder.append(", getSecureSocketProtocolsFilter()=");
        builder.append(getSecureSocketProtocolsFilter());
        builder.append(", getSessionTimeout()=");
        builder.append(getSessionTimeout());
        builder.append("]");
        return builder.toString();
    }
}
