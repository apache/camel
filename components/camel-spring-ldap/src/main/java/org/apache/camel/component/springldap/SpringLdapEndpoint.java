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

import javax.naming.directory.SearchControls;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.springframework.ldap.core.LdapTemplate;

/**
 * The spring-ldap component allows you to perform searches in LDAP servers using filters as the message payload.
 */
@UriEndpoint(firstVersion = "2.11.0", scheme = "spring-ldap", title = "Spring LDAP", syntax = "spring-ldap:templateName", producerOnly = true, label = "spring,ldap")
public class SpringLdapEndpoint extends DefaultEndpoint {

    private static final String OBJECT_SCOPE_NAME = "object";
    private static final String ONELEVEL_SCOPE_NAME = "onelevel";
    private static final String SUBTREE_SCOPE_NAME = "subtree";

    private LdapTemplate ldapTemplate;
    @UriPath @Metadata(required = "true")
    private String templateName;
    @UriParam @Metadata(required = "true")
    private LdapOperation operation;
    @UriParam(defaultValue = "subtree", enums = "object,onelevel,subtree")
    private String scope = SUBTREE_SCOPE_NAME;

    /**
     * Initializes the SpringLdapEndpoint using the provided template
     *
     * @param templateName name of the LDAP template
     * @param ldapTemplate LDAP template, see org.springframework.ldap.core.LdapTemplate
     */
    public SpringLdapEndpoint(String templateName, LdapTemplate ldapTemplate) {
        this.templateName = templateName;
        this.ldapTemplate = ldapTemplate;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpringLdapProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("spring-ldap endpoint supports producer enrpoint only.");
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public String createEndpointUri() {
        return "spring-ldap://" + templateName + "?operation=" + operation.name() + "&scope=" + getScope();
    }

    public LdapTemplate getLdapTemplate() {
        return ldapTemplate;
    }

    public String getTemplateName() {
        return templateName;
    }

    /**
     * Name of the Spring LDAP Template bean
     */
    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public LdapOperation getOperation() {
        return operation;
    }

    /**
     * The LDAP operation to be performed.
     */
    public void setOperation(LdapOperation operation) {
        this.operation = operation;
    }

    public String getScope() {
        return scope;
    }

    /**
     * The scope of the search operation.
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    public int scopeValue() {
        if (scope.equals(OBJECT_SCOPE_NAME)) {
            return SearchControls.OBJECT_SCOPE;
        } else if (scope.equals(ONELEVEL_SCOPE_NAME)) {
            return SearchControls.ONELEVEL_SCOPE;
        } else {
            return SearchControls.SUBTREE_SCOPE;
        }
    }
}
