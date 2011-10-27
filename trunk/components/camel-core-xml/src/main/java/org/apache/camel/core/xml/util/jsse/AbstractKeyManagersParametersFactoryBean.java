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

import javax.net.ssl.KeyManager;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.util.jsse.KeyManagersParameters;

/**
 * A representation of configuration options for creating and loading
 * {@link KeyManager} instance(s).
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractKeyManagersParametersFactoryBean extends AbstractJsseUtilFactoryBean<KeyManagersParameters> {
    
    @XmlAttribute
    protected String keyPassword;
    
    @XmlAttribute
    protected String provider;
    
    @XmlAttribute
    protected String algorithm;
    
    @XmlTransient
    private KeyManagersParameters instance;

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String value) {
        this.keyPassword = value;
    }

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
    
    @Override
    public KeyManagersParameters getObject() throws Exception {
        if (this.isSingleton()) {
            if (instance == null) { 
                instance = createInstance();   
            }
            
            return instance;
        } else {
            return createInstance();
        }   
    }

    @Override
    public Class<? extends KeyManagersParameters> getObjectType() {
        return KeyManagersParameters.class;
    }

    protected KeyManagersParameters createInstance() throws Exception {
        KeyManagersParameters newInstance = new KeyManagersParameters();
        
        newInstance.setAlgorithm(algorithm);
        newInstance.setKeyPassword(keyPassword);
        if (getKeyStore() != null) {
            getKeyStore().setCamelContext(getCamelContext());
            newInstance.setKeyStore(getKeyStore().getObject());
        }
        newInstance.setProvider(provider);
        newInstance.setCamelContext(getCamelContext());
        
        return newInstance;
    }
    
    public abstract AbstractKeyStoreParametersFactoryBean getKeyStore();
}
