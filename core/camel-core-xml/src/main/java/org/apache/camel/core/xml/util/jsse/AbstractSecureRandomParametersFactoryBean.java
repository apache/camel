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

import org.apache.camel.spi.Metadata;
import org.apache.camel.support.jsse.SecureRandomParameters;

@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractSecureRandomParametersFactoryBean extends AbstractJsseUtilFactoryBean<SecureRandomParameters> {

    @XmlAttribute(required = true)
    @Metadata(description = "The Random Number Generator algorithm identifier for the SecureRandom factory method used to create the SecureRandom represented by this object's configuration."
                            + " See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html")
    protected String algorithm;

    @XmlAttribute
    @Metadata(label = "advanced", description = "The provider identifier for the SecureRandom factory"
                                                + " method used to create the SecureRandom represented by this object's configuration.")
    protected String provider;

    @XmlTransient
    private SecureRandomParameters instance;

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    @Override
    public SecureRandomParameters getObject() throws Exception {
        if (this.isSingleton()) {
            if (instance == null) {
                instance = createInstance();
            }

            return instance;
        } else {
            return createInstance();
        }
    }

    protected SecureRandomParameters createInstance() {
        SecureRandomParameters newInstance = new SecureRandomParameters();

        newInstance.setAlgorithm(algorithm);
        newInstance.setProvider(provider);
        newInstance.setCamelContext(getCamelContext());

        return newInstance;
    }

    @Override
    public Class<? extends SecureRandomParameters> getObjectType() {
        return SecureRandomParameters.class;
    }
}
