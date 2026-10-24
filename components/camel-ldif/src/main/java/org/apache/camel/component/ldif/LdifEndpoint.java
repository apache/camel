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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform updates on an LDAP server from an LDIF body content.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "ldif", title = "LDIF", syntax = "ldif:ldapConnectionName", producerOnly = true,
             category = { Category.DATABASE, Category.SECURITY })
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  description = "The LDIF text, starting with version: 1, or bytes, a stream or a file of it."
                                                + " With allowUrlBody, a text that is not LDIF is read as the URL of the LDIF"
                                                + " file; otherwise it fails, as does a Map or a POJO"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.NULL },
                                   description = "A List of one status String per LDIF entry: success, or the error message from"
                                                 + " the server, which can be empty. No body or an empty body gives null"))
public class LdifEndpoint extends DefaultEndpoint {
    @UriPath
    @Metadata(required = true)
    private String ldapConnectionName;
    @UriParam(label = "security", defaultValue = "false", security = "insecure:dev")
    private boolean allowUrlBody;

    protected LdifEndpoint(String endpointUri, String remaining, LdifComponent component) {
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
     * The name of the LdapConnection bean to pull from the registry. Note that this must be of scope "prototype" to
     * avoid it being shared among threads or using a connection that has timed out.
     */
    public void setLdapConnectionName(String ldapConnectionName) {
        this.ldapConnectionName = ldapConnectionName;
    }

    public boolean isAllowUrlBody() {
        return allowUrlBody;
    }

    /**
     * Whether to allow a message body that is not LDIF content to be dereferenced as a URL and fetched. When disabled
     * (default), a body that does not start with <tt>version: 1</tt> is rejected with an
     * {@link IllegalArgumentException} instead of being fetched as a URL, which avoids a content-sniffed URL fetch
     * (SSRF) from untrusted body content.
     */
    public void setAllowUrlBody(boolean allowUrlBody) {
        this.allowUrlBody = allowUrlBody;
    }
}
