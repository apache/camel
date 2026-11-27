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
 * Apply the changes of an LDIF message body to an LDAP server.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "ldif", title = "LDIF", syntax = "ldif:ldapConnectionName", producerOnly = true,
             category = { Category.DATABASE, Category.SECURITY })
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  description = "The LDIF text, starting with version: 1, or bytes, a stream or a file of it."
                                                + " With allowUrlBody, a text that is not LDIF is read as the URL of the LDIF"
                                                + " file; otherwise it fails, as does a Map or a POJO"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.TEXT },
                                   description = "A List of one status String per LDIF entry: success, or the LDAP result code,"
                                                 + " the DN and the message from the server, such as entryAlreadyExists (68) on"
                                                 + " uid=a,dc=example,dc=org. No body or an empty body gives an empty String"))
public class LdifEndpoint extends DefaultEndpoint {
    @UriPath(description = "The name of the org.apache.directory.ldap.client.api.LdapConnection bean in the registry. The"
                           + " producer looks it up for each message, binds it with its configured name and credentials"
                           + " when it is not authenticated, and closes it after the message. Use a bean of scope prototype,"
                           + " so that concurrent messages do not share a connection.")
    @Metadata(required = true)
    private String ldapConnectionName;
    @UriParam(description = "Whether the producer reads a message body that does not start with version: 1 as the URL of an"
                            + " LDIF file, and applies the LDIF content that it fetches from that URL. If false, such a body"
                            + " fails with an IllegalArgumentException. Enable it only for a trusted body, as the producer"
                            + " fetches any URL that the body names (SSRF).",
              label = "security", defaultValue = "false", security = "insecure:dev")
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

    public void setLdapConnectionName(String ldapConnectionName) {
        this.ldapConnectionName = ldapConnectionName;
    }

    public boolean isAllowUrlBody() {
        return allowUrlBody;
    }

    public void setAllowUrlBody(boolean allowUrlBody) {
        this.allowUrlBody = allowUrlBody;
    }
}
