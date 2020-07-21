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
package org.apache.camel.core.xml.util.jsse;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.support.jsse.SSLContextParameters;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlTransient
public abstract class AbstractSSLContextParametersFactoryBean extends AbstractBaseSSLContextParametersFactoryBean<SSLContextParameters> {
    
    @XmlAttribute
    @Metadata(description = "The optional provider identifier for the JSSE implementation to use when constructing an SSLContext.")
    private String provider;

    @XmlAttribute
    @Metadata(description = "The optional protocol for the secure sockets created by the SSLContext"
            + " represented by this instance's configuration. See Appendix A in the Java Secure Socket Extension Reference Guide for information about standard protocol names.")
    private String secureSocketProtocol;
    
    @XmlAttribute
    @Metadata(description = "An optional certificate alias to use. This is useful when the keystore has multiple certificates.")
    private String certAlias;

    @Override
    protected SSLContextParameters createInstance() throws Exception {
        SSLContextParameters newInstance = new SSLContextParameters();
        
        if (getKeyManagers() != null) {
            getKeyManagers().setCamelContext(getCamelContext());
            newInstance.setKeyManagers(getKeyManagers().getObject());
        }
        
        if (getTrustManagers() != null) {
            getTrustManagers().setCamelContext(getCamelContext());
            newInstance.setTrustManagers(getTrustManagers().getObject());
        }
        
        if (getSecureRandom() != null) {
            getSecureRandom().setCamelContext(getCamelContext());
            newInstance.setSecureRandom(getSecureRandom().getObject());
        }
        
        
        if (getClientParameters() != null) {
            getClientParameters().setCamelContext(getCamelContext());
            newInstance.setClientParameters(getClientParameters().getObject());
        }

        if (getServerParameters() != null) {
            getServerParameters().setCamelContext(getCamelContext());
            newInstance.setServerParameters(getServerParameters().getObject());
        }
        
        newInstance.setProvider(provider);
        newInstance.setSecureSocketProtocol(secureSocketProtocol);
        newInstance.setCertAlias(certAlias);
        newInstance.setCamelContext(getCamelContext());
        
        return newInstance;
    }

    @Override
    public Class<? extends SSLContextParameters> getObjectType() {
        return SSLContextParameters.class;
    }
    
    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSecureSocketProtocol() {
        return secureSocketProtocol;
    }

    public void setSecureSocketProtocol(String secureSocketProtocol) {
        this.secureSocketProtocol = secureSocketProtocol;
    }
    
    public String getCertAlias() {
        return certAlias;
    }

    public void setCertAlias(String certAlias) {
        this.certAlias = certAlias;
    }

    protected abstract AbstractKeyManagersParametersFactoryBean getKeyManagers();

    protected abstract AbstractTrustManagersParametersFactoryBean getTrustManagers();

    protected abstract AbstractSecureRandomParametersFactoryBean getSecureRandom();

    protected abstract AbstractSSLContextClientParametersFactoryBean getClientParameters();

    protected abstract AbstractSSLContextServerParametersFactoryBean getServerParameters();
}
