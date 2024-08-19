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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;

import javax.net.ssl.TrustManager;

import org.apache.camel.spi.Metadata;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.jsse.TrustManagersParameters;

@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractTrustManagersParametersFactoryBean extends AbstractJsseUtilFactoryBean<TrustManagersParameters> {

    @XmlAttribute
    @Metadata(label = "advanced", description = "The provider identifier for the TrustManagerFactory used to create"
                                                + " TrustManagers represented by this object's configuration.")
    protected String provider;

    @XmlAttribute
    @Metadata(label = "advanced", description = "The optional algorithm name for the TrustManagerFactory used to"
                                                + " create the TrustManagers represented by this objects configuration."
                                                + " See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html")
    protected String algorithm;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "To use a existing configured trust manager instead of using TrustManagerFactory to get the TrustManager.")
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
