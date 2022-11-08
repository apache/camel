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
package org.apache.camel.component.ldap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdapProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(LdapProducer.class);

    private final String remaining;
    private final SearchControls searchControls;
    private final String searchBase;
    private final Integer pageSize;

    public LdapProducer(LdapEndpoint endpoint, String remaining, String base, int scope, Integer pageSize,
                        String returnedAttributes) {
        super(endpoint);

        this.remaining = remaining;
        this.searchBase = base;
        this.pageSize = pageSize;

        this.searchControls = new SearchControls();
        this.searchControls.setSearchScope(scope);
        if (returnedAttributes != null) {
            String[] atts = returnedAttributes.split(",");
            if (LOG.isDebugEnabled()) {
                LOG.debug("Setting returning Attributes to searchControls: {}", Arrays.toString(atts));
            }
            searchControls.setReturningAttributes(atts);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String filter = exchange.getIn().getBody(String.class);
        if (filter != null) {
            // filter must be LDAP escaped
            filter = escapeFilter(filter);
        }
        DirContext dirContext = getDirContext();

        try {
            // could throw NamingException
            List<SearchResult> data;
            if (pageSize == null) {
                data = simpleSearch(dirContext, filter);
            } else {
                if (!(dirContext instanceof LdapContext)) {
                    throw new IllegalArgumentException(
                            "When using attribute 'pageSize' for a ldap endpoint, you must provide a LdapContext (subclass of DirContext)");
                }
                data = pagedSearch((LdapContext) dirContext, filter);
            }
            exchange.getMessage().setBody(data);
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
        } finally {
            if (dirContext != null) {
                dirContext.close();
            }
        }
    }

    protected DirContext getDirContext() throws NamingException {
        // Obtain our ldap context. We do this by looking up the context in our registry.
        // Note though that a new context is expected each time. Therefore if spring is
        // being used then use prototype="scope". If you do not then you might experience
        // concurrency issues as InitialContext is not required to support concurrency.
        // On the other hand if you have a DirContext that is able to support concurrency
        // then using the default singleton scope is entirely sufficient. Most DirContext
        // classes will require prototype scope though.
        // if its a Map/Hashtable then we create a new context per time

        DirContext answer = null;
        Object context = getEndpoint().getCamelContext().getRegistry().lookupByName(remaining);
        if (context instanceof Hashtable) {
            answer = new InitialDirContext((Hashtable<?, ?>) context);
        } else if (context instanceof Map) {
            Hashtable hash = new Hashtable((Map) context);
            answer = new InitialDirContext(hash);
        } else if (context instanceof DirContext) {
            answer = (DirContext) context;
        } else if (context != null) {
            String msg = "Found bean: " + remaining + " in Registry of type: " + context.getClass().getName()
                         + " expected type was: " + DirContext.class.getName();
            throw new NoSuchBeanException(msg);
        }
        return answer;
    }

    private List<SearchResult> simpleSearch(DirContext ldapContext, String searchFilter) throws NamingException {
        List<SearchResult> data = new ArrayList<>();
        NamingEnumeration<SearchResult> namingEnumeration = ldapContext.search(searchBase, searchFilter, searchControls);
        while (namingEnumeration != null && namingEnumeration.hasMore()) {
            data.add(namingEnumeration.next());
        }
        return data;
    }

    private List<SearchResult> pagedSearch(LdapContext ldapContext, String searchFilter) throws Exception {
        List<SearchResult> data = new ArrayList<>();

        LOG.trace("Using paged ldap search, pageSize={}", pageSize);

        Control[] requestControls = new Control[] { new PagedResultsControl(pageSize, Control.CRITICAL) };
        ldapContext.setRequestControls(requestControls);
        do {
            List<SearchResult> pageResult = simpleSearch(ldapContext, searchFilter);
            data.addAll(pageResult);
            LOG.trace("Page returned {} entries", pageResult.size());
        } while (prepareNextPage(ldapContext));

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found a total of {} entries for ldap filter {}", data.size(), searchFilter);
        }

        return data;
    }

    private boolean prepareNextPage(LdapContext ldapContext) throws Exception {
        Control[] responseControls = ldapContext.getResponseControls();

        byte[] cookie = null;
        if (responseControls != null) {
            for (Control responseControl : responseControls) {
                if (responseControl instanceof PagedResultsResponseControl) {
                    PagedResultsResponseControl prrc = (PagedResultsResponseControl) responseControl;
                    cookie = prrc.getCookie();
                }
            }
        }

        if (cookie == null) {
            return false;
        } else {
            ldapContext.setRequestControls(new Control[] { new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
            return true;
        }
    }

    /**
     * Given an LDAP search string, returns the string with certain characters
     * escaped according to RFC 2254 guidelines.
     *
     * The character mapping is as follows:
     *     char -&gt;  Replacement
     *    ---------------------------
     *     *  -&gt; \2a
     *     (  -&gt; \28
     *     )  -&gt; \29
     *     \  -&gt; \5c
     *     \0 -&gt; \00
     *
     * @param filter string to escape according to RFC 2254 guidelines
     * @return String the escaped/encoded result
     */
    private String escapeFilter(String filter) {
        if (filter == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder(filter.length());
        for (int i = 0; i < filter.length(); i++) {
            char c = filter.charAt(i);
            switch (c) {
                case '\\':
                    buf.append("\\5c");
                    break;
                case '*':
                    buf.append("\\2a");
                    break;
                case '(':
                    buf.append("\\28");
                    break;
                case ')':
                    buf.append("\\29");
                    break;
                case '\0':
                    buf.append("\\00");
                    break;
                default:
                    buf.append(c);
                    break;
            }
        }
        return buf.toString();
    }

}
