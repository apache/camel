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
package org.apache.camel.util.spring;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.core.xml.util.jsse.AbstractSSLContextParametersFactoryBean;
import org.apache.camel.spring.util.CamelContextResolverHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@XmlRootElement(name = "sslContextParameters")
@XmlType(propOrder = {})
public class SSLContextParametersFactoryBean extends AbstractSSLContextParametersFactoryBean
        implements FactoryBean<SSLContextParameters>, ApplicationContextAware {
    
    private KeyManagersParametersFactoryBean keyManagers;
    
    private TrustManagersParametersFactoryBean trustManagers;
        
    private SecureRandomParametersFactoryBean secureRandom;
    
    private SSLContextClientParametersFactoryBean clientParameters;
    
    private SSLContextServerParametersFactoryBean serverParameters;
    
    @XmlTransient
    private ApplicationContext applicationContext;
    
    @Override
    public KeyManagersParametersFactoryBean getKeyManagers() {
        return keyManagers;
    }
    
    public void setKeyManagers(KeyManagersParametersFactoryBean keyManagers) {
        this.keyManagers = keyManagers;
    }

    @Override
    public TrustManagersParametersFactoryBean getTrustManagers() {
        return trustManagers;
    }
    
    public void setTrustManagers(TrustManagersParametersFactoryBean trustManagers) {
        this.trustManagers = trustManagers;
    }

    @Override
    public SecureRandomParametersFactoryBean getSecureRandom() {
        return secureRandom;
    }

    public void setSecureRandom(SecureRandomParametersFactoryBean secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public SSLContextClientParametersFactoryBean getClientParameters() {
        return clientParameters;
    }

    public void setClientParameters(SSLContextClientParametersFactoryBean clientParameters) {
        this.clientParameters = clientParameters;
    }

    @Override
    public SSLContextServerParametersFactoryBean getServerParameters() {
        return serverParameters;
    }
    
    public void setServerParameters(SSLContextServerParametersFactoryBean serverParameters) {
        this.serverParameters = serverParameters;
    }
    
    @Override
    protected CamelContext getCamelContextWithId(String camelContextId) {
        return CamelContextResolverHelper.getCamelContextWithId(applicationContext, camelContextId);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
