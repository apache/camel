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
package org.apache.camel.component.springldap;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Registry;
import org.springframework.ldap.core.LdapTemplate;

/**
 * Creates endpoints for the Spring LDAP component.
 */
public class SpringLdapComponent extends UriEndpointComponent {

    public SpringLdapComponent() {
        super(SpringLdapEndpoint.class);
    }

    /**
     * creates a Spring LDAP endpoint
     * @param remaining name of the Spring LDAP template bean to be used for the LDAP operation
     * @param parameters key-value pairs to be set on @see org.apache.camel.component.springldap.SpringLdapEndpoint.
     * Currently supported keys are operation and scope.
     * 'operation' is defined in org.apache.camel.component.springldap.LdapOperation.
     * 'scope' must be one of "object", "onelevel", or "subtree".
     */
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        CamelContext camelContext = getCamelContext();

        Registry registry = camelContext.getRegistry();
        LdapTemplate ldapTemplate = registry.lookupByNameAndType(remaining, LdapTemplate.class);

        Endpoint endpoint = new SpringLdapEndpoint(remaining, ldapTemplate);
        setProperties(endpoint, parameters);
        return endpoint;
    }

}
