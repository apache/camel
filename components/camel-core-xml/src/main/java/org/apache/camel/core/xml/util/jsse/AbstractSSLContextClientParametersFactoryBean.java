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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.util.jsse.SSLContextClientParameters;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlTransient
public abstract class AbstractSSLContextClientParametersFactoryBean extends AbstractBaseSSLContextParametersFactoryBean<SSLContextClientParameters> {

    @XmlElement(name = "sniHostNames")
    private SNIHostNamesDefinition sniHostNamesDefinition;

    @Override
    protected SSLContextClientParameters createInstance() {
        SSLContextClientParameters newInstance = new SSLContextClientParameters();
        newInstance.setCamelContext(getCamelContext());
        if (sniHostNamesDefinition != null) {
            newInstance.addAllSniHostNames(sniHostNamesDefinition.getSniHostName());
        }
        return newInstance;
    }

    @Override
    public Class<SSLContextClientParameters> getObjectType() {
        return SSLContextClientParameters.class;
    }

    public SNIHostNamesDefinition getSniHostNamesDefinition() {
        return sniHostNamesDefinition;
    }
}
