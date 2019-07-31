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
package org.apache.camel.component.ldif;

import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The ldif component allows you to do updates on an LDAP server from a LDIF body content.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "ldif", title = "LDIF", syntax = "ldif:ldapConnectionName", producerOnly = true, label = "ldap")
public class LdifEndpoint extends DefaultEndpoint {
    @UriPath
    @Metadata(required = true)
    private String ldapConnectionName;

    protected LdifEndpoint(String endpointUri, String remaining, LdifComponent component) throws URISyntaxException {
        super(endpointUri, component);
        this.ldapConnectionName = remaining;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("An LDIF Consumer would be the LDAP server itself! No such support here");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LdifProducer(this, ldapConnectionName);
    }

    public String getLdapConnectionName() {
        return ldapConnectionName;
    }

    /**
     * The name of the LdapConnection bean to pull from the registry. Note that
     * this must be of scope "prototype" to avoid it being shared among threads
     * or using a connection that has timed out.
     */
    public void setLdapConnectionName(String ldapConnectionName) {
        this.ldapConnectionName = ldapConnectionName;
    }
}
