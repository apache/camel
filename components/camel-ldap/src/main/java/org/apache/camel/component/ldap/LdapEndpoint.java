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
package org.apache.camel.component.ldap;

import java.net.URISyntaxException;
import java.util.Map;
import javax.naming.directory.SearchControls;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The ldap component allows you to perform searches in LDAP servers using filters as the message payload.
 */
@UriEndpoint(firstVersion = "1.5.0", scheme = "ldap", title = "LDAP", syntax = "ldap:dirContextName", producerOnly = true, label = "ldap")
public class LdapEndpoint extends DefaultEndpoint {
    public static final String SYSTEM_DN = "ou=system";
    public static final String OBJECT_SCOPE = "object";
    public static final String ONELEVEL_SCOPE = "onelevel";
    public static final String SUBTREE_SCOPE = "subtree";

    @UriPath @Metadata(required = "true")
    private String dirContextName;
    @UriParam(defaultValue = SYSTEM_DN)
    private String base = SYSTEM_DN;
    @UriParam(defaultValue = SUBTREE_SCOPE, enums = "object,onelevel,subtree")
    private String scope = SUBTREE_SCOPE;
    @UriParam
    private Integer pageSize;
    @UriParam
    private String returnedAttributes;

    protected LdapEndpoint(String endpointUri, String remaining, LdapComponent component) throws URISyntaxException {
        super(endpointUri, component);
        this.dirContextName = remaining;
    }

    @SuppressWarnings("deprecation")
    public LdapEndpoint(String endpointUri, String remaining) throws URISyntaxException {
        super(endpointUri);
        this.dirContextName = remaining;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("An LDAP Consumer would be the LDAP server itself! No such support here");
    }

    public Producer createProducer() throws Exception {
        return new LdapProducer(this, dirContextName, base, toSearchControlScope(scope), pageSize, returnedAttributes);
    }

    public boolean isSingleton() {
        return true;
    }

    public String getDirContextName() {
        return dirContextName;
    }

    /**
     * Name of either a {@link javax.naming.directory.DirContext}, or {@link java.util.Hashtable}, or {@link Map} bean to lookup in the registry.
     * If the bean is either a Hashtable or Map then a new {@link javax.naming.directory.DirContext} instance is created for each use. If the bean
     * is a {@link javax.naming.directory.DirContext} then the bean is used as given. The latter may not be possible in all situations where
     * the {@link javax.naming.directory.DirContext} must not be shared, and in those situations it can be better to use {@link java.util.Hashtable} or {@link Map} instead.
     */
    public void setDirContextName(String dirContextName) {
        this.dirContextName = dirContextName;
    }

    /**
     * When specified the ldap module uses paging to retrieve all results (most LDAP Servers throw an exception when trying to retrieve more than 1000 entries in one query).
     * To be able to use this a LdapContext (subclass of DirContext) has to be passed in as ldapServerBean (otherwise an exception is thrown)
     */
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public int getPageSize() {
        return pageSize;
    }

    public String getBase() {
        return base;
    }

    /**
     * The base DN for searches.
     */
    public void setBase(String base) {
        this.base = base;
    }

    public String getScope() {
        return scope;
    }

    /**
     * Specifies how deeply to search the tree of entries, starting at the base DN.
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getReturnedAttributes() {
        return returnedAttributes;
    }

    /**
     * Comma-separated list of attributes that should be set in each entry of the result
     */
    public void setReturnedAttributes(String returnedAttributes) {
        this.returnedAttributes = returnedAttributes;
    }

    private int toSearchControlScope(String scope) {
        if (scope.equalsIgnoreCase(OBJECT_SCOPE)) {
            return SearchControls.OBJECT_SCOPE;
        } else if (scope.equalsIgnoreCase(ONELEVEL_SCOPE)) {
            return SearchControls.ONELEVEL_SCOPE;
        } else if (scope.equalsIgnoreCase(SUBTREE_SCOPE)) {
            return SearchControls.SUBTREE_SCOPE;
        } else {
            throw new IllegalArgumentException("Invalid search scope \"" + scope
                + "\" for LdapEndpoint: " + getEndpointUri());
        }
    }
}
