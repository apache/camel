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

import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $
 */
public class LdapProducer<E extends Exchange> extends DefaultProducer<DefaultExchange> {
    private static final transient Log LOG = LogFactory.getLog(LdapProducer.class);
    private DirContext ldapContext;
    private SearchControls controls;
    private String searchBase;

    public LdapProducer(LdapEndpoint endpoint, String remaining, String base, int scope) throws Exception {
        super(endpoint);

        ldapContext = (DirContext)getEndpoint().getCamelContext().getRegistry().lookup(remaining);
        searchBase = base;
        controls = new SearchControls();
        controls.setSearchScope(scope);
    }

    public void process(Exchange exchange) throws Exception {
        String filter = exchange.getIn().getBody(String.class);

        // could throw NamingException
        List<SearchResult> data = new ArrayList<SearchResult>();
        NamingEnumeration<SearchResult> namingEnumeration =
            ldapContext.search(searchBase, filter, getControls());

        while (namingEnumeration.hasMore()) {
            data.add(namingEnumeration.next());
        }
        exchange.getOut().setBody(data);
    }

    public DirContext getDirContext() {
        return ldapContext;
    }

    protected SearchControls getControls() {
        return controls;
    }
}
