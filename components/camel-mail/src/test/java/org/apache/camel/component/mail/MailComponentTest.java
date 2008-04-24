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
package org.apache.camel.component.mail;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;

/**
 * @version $Revision$
 */
public class MailComponentTest extends ContextTestSupport {

    public void testMailEndpointsAreConfiguredProperlyWhenUsingSmtp() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost:30/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 30, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getDestination()", "james@myhost", config.getDestination());
        assertEquals("folder", "INBOX", config.getFolderName());
    }

    public void testMailEndpointsAreConfiguredProperlyWhenUsingImap() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("imap://james@myhost:30/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "imap", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 30, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getDestination()", "james@myhost", config.getDestination());
        assertEquals("folder", "INBOX", config.getFolderName());
    }

    public void testMailEndpointsAreConfiguredProperlyWhenUsingPop() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("pop3://james@myhost:30/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "pop3", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 30, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getDestination()", "james@myhost", config.getDestination());
        assertEquals("folder", "INBOX", config.getFolderName());
    }

    public void testDefaultConfiguration() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost?password=secret");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", -1, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getDestination()", "james@myhost", config.getDestination());
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals("encoding", null, config.getDefaultEncoding());
        assertEquals("from", "camel@localhost", config.getFrom());
        assertEquals("password", "secret", config.getPassword());
        assertEquals(true, config.isDeleteProcessedMessages());
        assertEquals(false, config.isIgnoreUriScheme());
    }

    public void testManyConfigurations() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint(
            "smtp://james@myhost:30/subject?password=secret&from=me@camelriders.org&DeleteProcessedMessages=false&defaultEncoding=iso-8859-1&folderName=riders");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 30, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getDestination()", "james@myhost", config.getDestination());
        assertEquals("folder", "riders", config.getFolderName());
        assertEquals("encoding", "iso-8859-1", config.getDefaultEncoding());
        assertEquals("from", "me@camelriders.org", config.getFrom());
        assertEquals("password", "secret", config.getPassword());
        assertEquals(false, config.isDeleteProcessedMessages());
        assertEquals(false, config.isIgnoreUriScheme());
    }

    @Override
    protected MailEndpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = super.resolveMandatoryEndpoint(uri);
        return assertIsInstanceOf(MailEndpoint.class, endpoint);
    }
}
