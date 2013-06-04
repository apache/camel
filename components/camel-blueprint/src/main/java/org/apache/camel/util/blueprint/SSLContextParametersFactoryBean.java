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
package org.apache.camel.util.blueprint;

import java.util.Set;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.blueprint.BlueprintCamelContextLookupHelper;
import org.apache.camel.core.xml.util.jsse.AbstractSSLContextParametersFactoryBean;
import org.osgi.service.blueprint.container.BlueprintContainer;

@XmlRootElement(name = "sslContextParameters")
@XmlType(propOrder = {})
public class SSLContextParametersFactoryBean extends AbstractSSLContextParametersFactoryBean {
    
    private KeyManagersParametersFactoryBean keyManagers;
    private TrustManagersParametersFactoryBean trustManagers;
    private SecureRandomParametersFactoryBean secureRandom;
    private SSLContextClientParametersFactoryBean clientParameters;
    private SSLContextServerParametersFactoryBean serverParameters;
    @XmlTransient
    private BlueprintContainer blueprintContainer;

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
    
    public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }
    
    @Override
    protected CamelContext getCamelContextWithId(String camelContextId) {
        if (blueprintContainer != null) {
            return (CamelContext) blueprintContainer.getComponentInstance(camelContextId);
        }
        return null;
    }

    @Override
    protected CamelContext discoverDefaultCamelContext() {
        if (blueprintContainer != null) {
            Set<String> ids = BlueprintCamelContextLookupHelper.lookupBlueprintCamelContext(blueprintContainer);
            if (ids.size() == 1) {
                // there is only 1 id for a BlueprintCamelContext so fallback and use this
                return getCamelContextWithId(ids.iterator().next());
            }
        }
        return null;
    }
}
