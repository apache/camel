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

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdifProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(LdifProducer.class);

    // Constants
    private static final String LDIF_HEADER = "version: 1";

    // properties
    private String ldapConnectionName;

    public LdifProducer(LdifEndpoint endpoint, String ldapConnectionName) throws Exception {
        super(endpoint);
        this.ldapConnectionName = ldapConnectionName;
    }

    /**
     * Process the body. There are two options:
     * <ol>
     * <li>A String body that is the LDIF content. This needs to start with
     * "version: 1".</li>
     * <li>A String body that is a URL to ready the LDIF content from</li>
     * </ol>
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        List<String> result = null;

        // Pass through everything
        exchange.setOut(exchange.getIn());

        // If nothing to do, then return an empty body
        if (ObjectHelper.isEmpty(body)) {
            exchange.getOut().setBody("");
        } else if (body.startsWith(LDIF_HEADER)) {
            LOG.debug("Reading from LDIF body");
            result = processLdif(new StringReader(body));
        } else {
            URL loc;
            try {
                loc = new URL(body);
                LOG.debug("Reading from URL: {}", loc);
                result = processLdif(new InputStreamReader(loc.openStream()));
            } catch (MalformedURLException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to parse body as URL and LDIF", e);
                }
                throw new InvalidPayloadException(exchange, String.class);
            }
        }

        exchange.getOut().setBody(result);
    }

    /**
     * Get the LdapConnection. Since the object is a factory, we'll just call
     * that. A future enhancement is to use the ApacheDS LdapConnectionPool
     * object to keep a pool of working connections that avoids the connection
     * pause.
     *
     * @return The created LDAP connection.
     */
    protected LdapConnection getLdapConnection() throws CamelException {
        return (LdapConnection)getEndpoint().getCamelContext().getRegistry().lookupByName(ldapConnectionName);
    }

    /**
     * Process an LDIF file from a reader.
     */
    private List<String> processLdif(Reader reader) throws CamelException {
        LdapConnection conn = getLdapConnection();
        LdifReader ldifReader;

        List<String> results = new ArrayList<>();

        // Create the reader
        try {
            ldifReader = new LdifReader(reader);
        } catch (LdapException e) {
            throw new CamelException("Unable to create LDIF reader", e);
        }

        // Process each entry
        for (LdifEntry e : ldifReader) {
            results.add(processLdifEntry(conn, e));
        }

        IOHelper.close(conn, ldifReader, reader);

        return results;
    }

    /**
     * Figure out the change is and what to do about it.
     *
     * @return A success/failure message
     */
    private String processLdifEntry(LdapConnection conn, LdifEntry ldifEntry) {
        try {
            if (ldifEntry.isChangeAdd() || ldifEntry.isLdifContent()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("attempting add of {}", ldifEntry);
                }
                conn.add(ldifEntry.getEntry());
            } else if (ldifEntry.isChangeModify()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("attempting modify of {}", ldifEntry);
                }
                conn.modify(ldifEntry.getDn(), ldifEntry.getModificationArray());
            } else if (ldifEntry.isChangeDelete()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("attempting delete of {}", ldifEntry);
                }
                conn.delete(ldifEntry.getDn());
            } else if (ldifEntry.isChangeModDn()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("attempting DN move of {}", ldifEntry);
                }
                conn.moveAndRename(ldifEntry.getDn(), new Dn(ldifEntry.getNewRdn(), ldifEntry.getNewSuperior()), ldifEntry.isDeleteOldRdn());
            } else if (ldifEntry.isChangeModRdn()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("attempting RDN move of {}", ldifEntry);
                }
                conn.rename(ldifEntry.getDn(), new Rdn(ldifEntry.getNewRdn()), ldifEntry.isDeleteOldRdn());
            }

            LOG.debug("ldif success");
            return "success";
        } catch (LdapException e) {
            LOG.debug("failed to apply ldif", e);
            return getRootCause(e);
        }
    }

    /**
     * Get the root cause of an exception
     */
    private String getRootCause(LdapException e) {
        Throwable oldt;
        Throwable thist;

        oldt = thist = e;
        while (thist != null) {
            oldt = thist;
            thist = thist.getCause();
        }
        return oldt.getMessage();
    }
}
