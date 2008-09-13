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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.util.jndi.JndiTest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.unit.AbstractServerTest;


/**
 * 
 */
public abstract class LdapTestSupport extends AbstractServerTest {
    protected transient Log log = LogFactory.getLog(getClass());
    protected CamelContext context;
    protected ProducerTemplate<Exchange> template;
    private boolean useRouteBuilder = true;
    private Service camelContextService;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        loadTestLdif(true);

        // disable JMX
        System.setProperty(JmxSystemPropertyKeys.DISABLED, "true");

        // create Camel context
        context = createCamelContext();
        assertValidContext(context);

        template = context.createProducerTemplate();
        if (useRouteBuilder) {
            RouteBuilder[] builders = createRouteBuilders();
            for (RouteBuilder builder : builders) {
                log.debug("Using created route builder: " + builder);
                context.addRoutes(builder);
            }
        } else {
            log.debug("Using route builder from the created context: " + context);
        }

        startCamelContext();

        log.debug("Routing Rules are: " + context.getRoutes());
    }

    @Override
    public void tearDown() throws Exception {
        log.debug("tearDown test: " + getName());
        template.stop();
        stopCamelContext();

        super.tearDown();
    }

    protected Set<SearchResult> getResults(DirContext ctx, String filter) throws Exception {
        Set<SearchResult> results = new HashSet<SearchResult>();
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> namingEnumeration = ctx.search(
            ServerDNConstants.SYSTEM_DN, filter, controls);
        while (namingEnumeration.hasMore()) {
            results.add(namingEnumeration.next());
        }
        return results;
    }

    protected boolean contains(String dn, Collection<SearchResult> results) {
        for (SearchResult result : results) {
            if (result.getNameInNamespace().equals(dn)) {
                return true;
            }
        }

        return false;
    }

    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext(createRegistry());
    }

    protected JndiRegistry createRegistry() throws Exception {
        return new JndiRegistry(createJndiContext());
    }

    protected Context createJndiContext() throws Exception {
        return JndiTest.createInitialContext();
    }

    protected void stopCamelContext() throws Exception {
        if (camelContextService != null) {
            camelContextService.stop();
        } else {
            context.stop();
        }
    }

    protected void startCamelContext() throws Exception {
        if (camelContextService != null) {
            camelContextService.start();
        } else {
            if (context instanceof DefaultCamelContext) {
                DefaultCamelContext defaultCamelContext = (DefaultCamelContext)context;
                if (!defaultCamelContext.isStarted()) {
                    defaultCamelContext.start();
                }
            } else {
                context.start();
            }
        }
    }

    protected void assertValidContext(CamelContext context) {
        assertNotNull("No context found!", context);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // no routes added by default
            }
        };
    }

    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {createRouteBuilder()};
    }
}
