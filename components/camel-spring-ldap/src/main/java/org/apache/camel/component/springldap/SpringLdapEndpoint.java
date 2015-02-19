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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.naming.directory.SearchControls;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.springframework.ldap.core.LdapTemplate;

@UriEndpoint(scheme = "spring-ldap", producerOnly = true, label = "spring,ldap")
public class SpringLdapEndpoint extends DefaultEndpoint {

    private static final String OBJECT_SCOPE_NAME = "object";
    private static final String ONELEVEL_SCOPE_NAME = "onelevel";
    private static final String SUBTREE_SCOPE_NAME = "subtree";

    private static Map<String, LdapOperation> operationMap;
    private static Map<String, Integer> scopeMap;

    private LdapTemplate ldapTemplate;
    @UriPath
    private String templateName;
    @UriParam
    private LdapOperation operation;
    @UriParam(defaultValue = "2")
    private int scope = SearchControls.SUBTREE_SCOPE;

    /**
     * Initializes the SpringLdapEndpoint using the provided template
     * @param templateName name of the LDAP template
     * @param ldapTemplate LDAP template, see org.springframework.ldap.core.LdapTemplate
     */
    public SpringLdapEndpoint(String templateName, LdapTemplate ldapTemplate) {
        this.templateName = templateName;
        this.ldapTemplate = ldapTemplate;
        if (null == operationMap) {
            initializeOperationMap();
        }

        if (null == scopeMap) {
            initializeScopeMap();
        }
    }

    private static void initializeScopeMap() {
        scopeMap = new HashMap<String, Integer>();

        scopeMap.put(OBJECT_SCOPE_NAME, SearchControls.OBJECT_SCOPE);
        scopeMap.put(ONELEVEL_SCOPE_NAME, SearchControls.ONELEVEL_SCOPE);
        scopeMap.put(SUBTREE_SCOPE_NAME, SearchControls.SUBTREE_SCOPE);
    }

    private static void initializeOperationMap() {
        operationMap = new HashMap<String, LdapOperation>();

        operationMap.put(LdapOperation.SEARCH.name(), LdapOperation.SEARCH);
        operationMap.put(LdapOperation.BIND.name(), LdapOperation.BIND);
        operationMap.put(LdapOperation.UNBIND.name(), LdapOperation.UNBIND);
    }

    /**
     * Creates a Producer using this SpringLdapEndpoint
     */
    @Override
    public Producer createProducer() throws Exception {
        return new SpringLdapProducer(this);
    }

    /**
     * Consumer endpoints are not supported.
     * @throw UnsupportedOperationException
     */
    
    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException(
                "spring-ldap endpoint supports producer enrpoint only.");
    }

    /**
     * returns false (constant)
     */
    @Override
    public boolean isSingleton() {
        return false;
    }

    int getScope() {
        return scope;
    }

    /**
     * sets the scope of the LDAP operation. The scope string must be one of "object = 0", "onelevel = 1", or "subtree = 2"
     */
    public void setScope(String scope) {
        for (Entry<String, Integer> allowedScope : scopeMap.entrySet()) {
            if (allowedScope.getKey().equals(scope)) {
                this.scope = allowedScope.getValue();
                return;
            }
        }
        throw new UnsupportedOperationException(
                "Search scope '"
                        + scope
                        + "' is not supported. The supported scopes are 'object', 'onelevel', and 'subtree'.");
    }

    LdapTemplate getLdapTemplate() {
        return ldapTemplate;
    }

    /**
     * @return URI used to create this endpoint 
     */
    @Override
    public String createEndpointUri() {
        return "spring-ldap://" + templateName + "?operation=" + operation.name() + "&scope=" + getScopeName();
    }

    private String getScopeName() {
        for (String key : scopeMap.keySet()) {
            if (scope == scopeMap.get(key)) {
                return key;
            }
        }
        
        throw new UnsupportedOperationException(
                "Search scope '"
                        + scope
                        + "' is not supported. The supported scopes are 'object', 'onelevel', and 'subtree'.");
        
    }

    LdapOperation getOperation() {
        return operation;
    }

    /**
     * Sets the LDAP operation to be performed. The supported operations are defined in org.apache.camel.component.springldap.LdapOperation.
     */
    public void setOperation(String operation) {
        for (Entry<String, LdapOperation> allowedOperation : operationMap
                .entrySet()) {
            if (allowedOperation.getKey().equalsIgnoreCase(operation)) {
                this.operation = allowedOperation.getValue();
                return;
            }
        }
        throw new UnsupportedOperationException(
                "LDAP operation '"
                        + operation
                        + "' is not supported. The supported operations are 'search', 'bind', and 'unbind'.");
    }
}
