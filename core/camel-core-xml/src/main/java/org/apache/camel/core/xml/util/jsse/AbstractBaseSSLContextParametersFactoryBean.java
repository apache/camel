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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.support.jsse.BaseSSLContextParameters;
import org.apache.camel.support.jsse.CipherSuitesParameters;
import org.apache.camel.support.jsse.FilterParameters;
import org.apache.camel.support.jsse.SecureSocketProtocolsParameters;

@XmlTransient
public abstract class AbstractBaseSSLContextParametersFactoryBean<T extends BaseSSLContextParameters> extends AbstractJsseUtilFactoryBean<T> {

    private CipherSuitesParametersDefinition cipherSuites;

    private FilterParametersDefinition cipherSuitesFilter;

    private SecureSocketProtocolsParametersDefinition secureSocketProtocols;

    private FilterParametersDefinition secureSocketProtocolsFilter;

    @XmlAttribute
    @Metadata(description = "The optional SSLSessionContext timeout time for javax.net.ssl.SSLSession in seconds.")
    private String sessionTimeout;

    @XmlTransient
    private T instance;

    @Override
    public final T getObject() throws Exception {
        if (this.isSingleton()) {
            if (instance == null) {
                instance = createInstanceInternal();
            }

            return instance;
        } else {
            return createInstanceInternal();
        }
    }

    protected abstract T createInstance() throws Exception;

    private T createInstanceInternal() throws Exception {
        T newInstance = createInstance();
        newInstance.setCamelContext(getCamelContext());

        if (cipherSuites != null) {
            CipherSuitesParameters cipherSuitesInstance = new CipherSuitesParameters();
            cipherSuitesInstance.setCipherSuite(cipherSuites.getCipherSuite());
            newInstance.setCipherSuites(cipherSuitesInstance);
        }

        if (cipherSuitesFilter != null) {
            newInstance.setCipherSuitesFilter(createFilterParameters(cipherSuitesFilter));
        }

        if (secureSocketProtocols != null) {
            SecureSocketProtocolsParameters secureSocketProtocolsInstance = new SecureSocketProtocolsParameters();
            secureSocketProtocolsInstance.setSecureSocketProtocol(secureSocketProtocols.getSecureSocketProtocol());
            newInstance.setSecureSocketProtocols(secureSocketProtocolsInstance);
        }

        if (secureSocketProtocolsFilter != null) {
            newInstance.setSecureSocketProtocolsFilter(createFilterParameters(secureSocketProtocolsFilter));
        }

        if (sessionTimeout != null) {
            newInstance.setSessionTimeout(sessionTimeout);
        }

        return newInstance;
    }

    private FilterParameters createFilterParameters(FilterParametersDefinition definition) {
        FilterParameters filter = new FilterParameters();
        filter.getInclude().addAll(definition.getInclude());
        filter.getExclude().addAll(definition.getExclude());
        filter.setCamelContext(getCamelContext());

        return filter;
    }

    public CipherSuitesParametersDefinition getCipherSuites() {
        return cipherSuites;
    }

    public void setCipherSuites(CipherSuitesParametersDefinition cipherSuites) {
        this.cipherSuites = cipherSuites;
    }

    public FilterParametersDefinition getCipherSuitesFilter() {
        return cipherSuitesFilter;
    }

    public void setCipherSuitesFilter(FilterParametersDefinition cipherSuitesFilter) {
        this.cipherSuitesFilter = cipherSuitesFilter;
    }

    public SecureSocketProtocolsParametersDefinition getSecureSocketProtocols() {
        return secureSocketProtocols;
    }

    public void setSecureSocketProtocols(SecureSocketProtocolsParametersDefinition secureSocketProtocols) {
        this.secureSocketProtocols = secureSocketProtocols;
    }

    public FilterParametersDefinition getSecureSocketProtocolsFilter() {
        return secureSocketProtocolsFilter;
    }

    public void setSecureSocketProtocolsFilter(FilterParametersDefinition secureSocketProtocolsFilter) {
        this.secureSocketProtocolsFilter = secureSocketProtocolsFilter;
    }

    public String getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(String sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }
}
