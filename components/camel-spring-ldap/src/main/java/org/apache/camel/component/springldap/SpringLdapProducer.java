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
package org.apache.camel.component.springldap;

import java.util.Map;
import java.util.function.BiFunction;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.ModificationItem;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.commons.lang.StringUtils;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.ldap.query.LdapQueryBuilder;

public class SpringLdapProducer extends DefaultProducer {

    public static final String DN = "dn";
    public static final String FILTER = "filter";
    public static final String ATTRIBUTES = "attributes";
    public static final String PASSWORD = "password";
    public static final String MODIFICATION_ITEMS = "modificationItems";

    public static final String FUNCTION = "function";
    public static final String REQUEST = "request";

    SpringLdapEndpoint endpoint;

    private AttributesMapper<Object> mapper = new AttributesMapper<Object>() {

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
     * Performs the LDAP operation defined in SpringLdapEndpoint that created
     * this producer. The in-message in the exchange must be a map, containing
     * the following entries:
     *
     * <pre>
     * key: "dn" - base DN for the LDAP operation
     * key: "filter" - necessary for the search operation only; LDAP filter for the search operation,
     * see <a http://en.wikipedia.org/wiki/Lightweight_Directory_Access_Protocol>http://en.wikipedia.org/wiki/Lightweight_Directory_Access_Protocol</a>
     * key: "attributes" - necessary for the bind operation only; an instance of javax.naming.directory.Attributes,
     * containing the information necessary to create an LDAP node.
     * key: "password" - necessary for the authentication operation only;
     * key: "modificationItems" - necessary for the modify_attributes operation only;
     * key: "function" - necessary for the function_driven operation only; provides a flexible hook into the {@link LdapTemplate} to call any method
     * key: "request" - necessary for the function_driven operation only; passed into the "function" to enable the client to bind parameters that need to be passed into the {@link LdapTemplate}
     * </pre>
     *
     * The keys are defined as final fields above.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = exchange.getIn().getBody(Map.class);

        LdapOperation operation = endpoint.getOperation();
        if (null == operation) {
            throw new UnsupportedOperationException("LDAP operation must not be empty, but you provided an empty operation");
        }

        LdapTemplate ldapTemplate = endpoint.getLdapTemplate();

        String dn = (String)body.get(DN);
        if (StringUtils.isBlank(dn)) {
            ContextSource contextSource = ldapTemplate.getContextSource();
            if (contextSource instanceof BaseLdapPathContextSource) {
                dn = ((BaseLdapPathContextSource) contextSource).getBaseLdapPathAsString();
            }
        }
        if (operation != LdapOperation.FUNCTION_DRIVEN && (StringUtils.isBlank(dn))) {
            throw new UnsupportedOperationException("DN must not be empty, but you provided an empty DN");
        }

        switch (operation) {
            case SEARCH:
                String filter = (String)body.get(FILTER);
                exchange.getIn().setBody(ldapTemplate.search(dn, filter, endpoint.scopeValue(), mapper));
                break;
            case BIND:
                Attributes attributes = (Attributes)body.get(ATTRIBUTES);
                ldapTemplate.bind(dn, null, attributes);
                break;
            case UNBIND:
                ldapTemplate.unbind(dn);
                break;
            case AUTHENTICATE:
                ldapTemplate.authenticate(LdapQueryBuilder.query().base(dn).filter((String)body.get(FILTER)), (String)body.get(PASSWORD));
                break;
            case MODIFY_ATTRIBUTES:
                ModificationItem[] modificationItems = (ModificationItem[])body.get(MODIFICATION_ITEMS);
                ldapTemplate.modifyAttributes(dn, modificationItems);
                break;
            case FUNCTION_DRIVEN:
                BiFunction<LdapOperations, Object, ?> ldapOperationFunction = (BiFunction<LdapOperations, Object, ?>)body.get(FUNCTION);
                Object ldapOperationRequest = body.get(REQUEST);
                exchange.getIn().setBody(ldapOperationFunction.apply(ldapTemplate, ldapOperationRequest));
                break;
            default:
                throw new UnsupportedOperationException("Bug in the Spring-LDAP component. Despite of all assertions, you managed to call an unsupported operation '" + operation
                        + "'");
        }
    }
}
