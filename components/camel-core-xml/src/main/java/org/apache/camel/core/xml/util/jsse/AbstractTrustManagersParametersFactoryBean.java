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
package org.apache.camel.core.xml.util.jsse;

import javax.net.ssl.TrustManager;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.jsse.TrustManagersParameters;

@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractTrustManagersParametersFactoryBean extends AbstractJsseUtilFactoryBean<TrustManagersParameters> {

    @XmlAttribute
    protected String provider;
    
    @XmlAttribute
    protected String algorithm;
    
    @XmlAttribute
    protected String trustManager;

    @XmlTransient
    private TrustManagersParameters instance;
    
    public String getProvider() {
        return provider;
    }

    public void setProvider(String value) {
        this.provider = value;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String value) {
        this.algorithm = value;
    }

    public String getTrustManager() {
        return trustManager;
    }

    public void setTrustManager(String trustManager) {
        this.trustManager = trustManager;
    }

    @Override
    public TrustManagersParameters getObject() throws Exception {
        if (isSingleton()) {
            if (instance == null) { 
                instance = createInstance();   
            }
            return instance;
        } else {
            return createInstance();
        }   
    }
    
    @Override
    public Class<TrustManagersParameters> getObjectType() {
        return TrustManagersParameters.class;
    }
    
    protected TrustManagersParameters createInstance() throws Exception {
        TrustManagersParameters newInstance = new TrustManagersParameters();
        
        newInstance.setAlgorithm(algorithm);
        if (getKeyStore() != null) {
            getKeyStore().setCamelContext(getCamelContext());
            newInstance.setKeyStore(getKeyStore().getObject());
        }
        newInstance.setProvider(provider);
        newInstance.setCamelContext(getCamelContext());

        if (trustManager != null) {
            TrustManager tm = CamelContextHelper.mandatoryLookup(getCamelContext(), trustManager, TrustManager.class);
            newInstance.setTrustManager(tm);
        }
        
        return newInstance;
    }
    
    protected abstract AbstractKeyStoreParametersFactoryBean getKeyStore();
}
