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

import javax.naming.NamingException;
import javax.naming.directory.Attributes;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

public class SpringLdapProducer extends DefaultProducer {

    public static final String DN = "dn";
    public static final String FILTER = "filter";
    public static final String ATTRIBUTES = "attributes";

    SpringLdapEndpoint endpoint;

    private AttributesMapper mapper = new AttributesMapper() {

        @Override
        public Object mapFromAttributes(Attributes attributes) throws NamingException {
            return attributes;
        }
    };

    /**
     * Initializes the SpringLdapProducer with the given endpoint
     */
    public SpringLdapProducer(SpringLdapEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    /**
     * Performs the LDAP operation defined in SpringLdapEndpoint that created this producer.
     * The in-message in the exchange must be a map, containing the following entries:
     * <pre>
     * key: "dn" - base DN for the LDAP operation
     * key: "filter" - necessary for the search operation only; LDAP filter for the search operation,
     * see <a http://en.wikipedia.org/wiki/Lightweight_Directory_Access_Protocol>http://en.wikipedia.org/wiki/Lightweight_Directory_Access_Protocol</a>
     * key: "attributes" - necessary for the bind operation only; an instance of javax.naming.directory.Attributes,
     * containing the information necessary to create an LDAP node.
     * </pre>
     * The keys are defined as final fields above.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        String dn = (String) body.get(DN);

        if (null == dn || dn.length() == 0) {
            throw new UnsupportedOperationException(
                    "DN must not be empty, but you provided an empty DN");
        }

        LdapOperation operation = endpoint.getOperation();
        LdapTemplate ldapTemplate = endpoint.getLdapTemplate();

        if (null == operation) {
            throw new UnsupportedOperationException(
                    "LDAP operation must not be empty, but you provided an empty operation");
        }

        switch (operation) {
        case SEARCH:
            String filter = (String) body.get(FILTER);
            exchange.getIn().setBody(ldapTemplate.search(dn, filter, endpoint.scopeValue(), mapper));
            break;
        case BIND:
            Attributes attributes = (Attributes) body.get(ATTRIBUTES);
            ldapTemplate.bind(dn, null, attributes);
            break;
        case UNBIND:
            ldapTemplate.unbind(dn);
            break;
        default:
            throw new UnsupportedOperationException(
                    "Bug in the Spring-LDAP component. Despite of all assertions, you managed to call an unsupported operation '"
                            + operation + "'");
        }
    }
}
