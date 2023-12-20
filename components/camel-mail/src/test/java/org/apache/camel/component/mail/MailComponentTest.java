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
package org.apache.camel.component.mail;

import jakarta.mail.Message;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MailComponentTest extends CamelTestSupport {

    @Test
    public void testMailEndpointsAreConfiguredProperlyWhenUsingSmtp() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost:25/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("smtp", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(25, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testMailEndpointsAreConfiguredProperlyWhenUsingImap() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("imap://james@myhost:143/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("imap", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(143, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testMailEndpointsAreConfiguredProperlyWhenUsingPop() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("pop3://james@myhost:110/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("pop3", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(110, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testDefaultSMTPConfiguration() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost?password=secret");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("smtp", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(MailUtils.DEFAULT_PORT_SMTP, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertEquals("camel@localhost", config.getFrom(), "from");
        assertEquals("secret", config.getPassword(), "password");
        assertFalse(config.isDelete());
        assertFalse(config.isIgnoreUriScheme());
        assertEquals(-1, config.getFetchSize(), "fetchSize");
        assertEquals("text/plain", config.getContentType(), MailConstants.MAIL_CONTENT_TYPE);
        assertEquals(true, config.isUnseen(), "unseen");
        assertFalse(config.isDebugMode());
        assertEquals(MailConstants.MAIL_DEFAULT_CONNECTION_TIMEOUT, config.getConnectionTimeout());
        assertFalse(config.isSecureProtocol());
        assertEquals("smtp://myhost:25, folder=INBOX", config.getMailStoreLogInformation());
    }

    @Test
    public void testDefaultSMTPSConfiguration() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtps://james@myhost?password=secret");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("smtps", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(MailUtils.DEFAULT_PORT_SMTPS, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertEquals("camel@localhost", config.getFrom(), "from");
        assertEquals("secret", config.getPassword(), "password");
        assertFalse(config.isDelete());
        assertFalse(config.isIgnoreUriScheme());
        assertEquals(-1, config.getFetchSize(), "fetchSize");
        assertEquals("text/plain", config.getContentType(), MailConstants.MAIL_CONTENT_TYPE);
        assertEquals(true, config.isUnseen(), "unseen");
        assertFalse(config.isDebugMode());
        assertEquals(MailConstants.MAIL_DEFAULT_CONNECTION_TIMEOUT, config.getConnectionTimeout());
        assertEquals(true, config.isSecureProtocol());
        assertEquals("smtps://myhost:465 (SSL enabled), folder=INBOX", config.getMailStoreLogInformation());
    }

    @Test
    public void testDebugMode() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost?password=secret&debugMode=true");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals(true, config.isDebugMode());
    }

    @Test
    public void testConnectionTimeout() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost?password=secret&connectionTimeout=2500");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals(2500, config.getConnectionTimeout());
    }

    @Test
    public void testDefaultPOP3Configuration() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("pop3://james@myhost?password=secret");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("pop3", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(MailUtils.DEFAULT_PORT_POP3, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertEquals("camel@localhost", config.getFrom(), "from");
        assertEquals("secret", config.getPassword(), "password");
        assertFalse(config.isDelete());
        assertFalse(config.isIgnoreUriScheme());
        assertEquals(-1, config.getFetchSize(), "fetchSize");
        assertEquals("text/plain", config.getContentType(), MailConstants.MAIL_CONTENT_TYPE);
        assertEquals(true, config.isUnseen(), "unseen");
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testDefaultIMAPConfiguration() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("imap://james@myhost?password=secret");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("imap", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(MailUtils.DEFAULT_PORT_IMAP, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertEquals("camel@localhost", config.getFrom(), "from");
        assertEquals("secret", config.getPassword(), "password");
        assertFalse(config.isDelete());
        assertFalse(config.isIgnoreUriScheme());
        assertEquals(-1, config.getFetchSize(), "fetchSize");
        assertEquals("text/plain", config.getContentType(), MailConstants.MAIL_CONTENT_TYPE);
        assertEquals(true, config.isUnseen(), "unseen");
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testManyConfigurations() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost:30/subject?password=secret"
                                                         + "&from=me@camelriders.org&delete=true&folderName=riders"
                                                         + "&contentType=text/html&unseen=false");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("smtp", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(30, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("riders", config.getFolderName(), "folder");
        assertEquals("me@camelriders.org", config.getFrom(), "from");
        assertEquals("secret", config.getPassword(), "password");
        assertEquals(true, config.isDelete());
        assertFalse(config.isIgnoreUriScheme());
        assertEquals(-1, config.getFetchSize(), "fetchSize");
        assertFalse(config.isUnseen(), "unseen");
        assertEquals("text/html", config.getContentType(), MailConstants.MAIL_CONTENT_TYPE);
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testTo() {
        MailEndpoint endpoint
                = resolveMandatoryEndpoint("smtp://james@myhost:25/?password=secret&to=someone@outthere.com&folderName=XXX");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("smtp", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(25, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("someone@outthere.com", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("XXX", config.getFolderName(), "folder");
        assertEquals("camel@localhost", config.getFrom(), "from");
        assertEquals("secret", config.getPassword(), "password");
        assertFalse(config.isDelete());
        assertFalse(config.isIgnoreUriScheme());
        assertEquals(-1, config.getFetchSize(), "fetchSize");
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testNoUserInfoButUsername() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://myhost:25/?password=secret&username=james");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("smtp", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(25, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertEquals("camel@localhost", config.getFrom(), "from");
        assertEquals("secret", config.getPassword(), "password");
        assertFalse(config.isDelete());
        assertFalse(config.isIgnoreUriScheme());
        assertEquals(-1, config.getFetchSize(), "fetchSize");
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testAuthenticator() {
        DefaultAuthenticator auth1 = new DefaultAuthenticator("u1", "p1");
        context.getRegistry().bind("auth1", auth1);
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://myhost:25/?authenticator=#auth1&to=james%40myhost");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("smtp", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(25, config.getPort(), "getPort()");
        assertNull(config.getUsername(), "getUsername()");
        assertNotNull(config.getPasswordAuthentication(), "getPasswordAuthentication()");
        assertEquals("u1", config.getPasswordAuthentication().getUserName(), "getPasswordAuthentication().getUserName()");
        assertEquals("p1", config.getPasswordAuthentication().getPassword(), "getPasswordAuthentication().getUserName()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertEquals("camel@localhost", config.getFrom(), "from");
        assertNull(config.getPassword(), "password");
        assertFalse(config.isDelete());
        assertFalse(config.isIgnoreUriScheme());
        assertEquals(-1, config.getFetchSize(), "fetchSize");
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testMailEndpointsWithFetchSize() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("pop3://james@myhost?fetchSize=5");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("pop3", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(110, config.getPort(), "getPort()");
        assertEquals("james", config.getUsername(), "getUsername()");
        assertEquals("james@myhost", config.getRecipients().get(Message.RecipientType.TO),
                "getRecipients().get(Message.RecipientType.TO)");
        assertEquals("INBOX", config.getFolderName(), "folder");
        assertEquals(5, config.getFetchSize(), "fetchSize");
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testSMTPEndpointWithSubjectOption() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://myhost:25?subject=hello");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("smtp", config.getProtocol(), "getProtocol()");
        assertEquals("myhost", config.getHost(), "getHost()");
        assertEquals(25, config.getPort(), "getPort()");
        assertEquals("hello", config.getSubject(), "getSubject()");
        assertFalse(config.isDebugMode());
    }

    @Override
    protected MailEndpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = super.resolveMandatoryEndpoint(uri);
        return assertIsInstanceOf(MailEndpoint.class, endpoint);
    }

    @Test
    public void testMailComponentCtr() throws Exception {
        MailComponent comp = new MailComponent();
        comp.setCamelContext(context);
        comp.init();

        assertNotNull(comp.getConfiguration());
        assertNull(comp.getContentTypeResolver());

        MailEndpoint endpoint = (MailEndpoint) comp.createEndpoint("smtp://myhost:25/?password=secret&username=james");
        assertNotNull(endpoint);

        // should be a copy of the configuration
        assertNotSame(comp.getConfiguration(), endpoint.getConfiguration());
    }

    @Test
    public void testMailComponentCtrCamelContext() throws Exception {
        MailComponent comp = new MailComponent(context);
        comp.init();

        assertNotNull(comp.getConfiguration());
        assertNull(comp.getContentTypeResolver());

        MailEndpoint endpoint = (MailEndpoint) comp.createEndpoint("smtp://myhost:25/?password=secret&username=james");
        assertNotNull(endpoint);

        // should be a copy of the configuration
        assertNotSame(comp.getConfiguration(), endpoint.getConfiguration());
    }

    @Test
    public void testMailComponentCtrConfig() throws Exception {
        MailConfiguration config = new MailConfiguration();
        config.setUsername("james");
        config.setPassword("secret");

        MailComponent comp = new MailComponent(config);
        comp.setCamelContext(context);

        assertSame(config, comp.getConfiguration());
        assertNull(comp.getContentTypeResolver());

        MailEndpoint endpoint = (MailEndpoint) comp.createEndpoint("smtp://myhost/");
        assertEquals("james", endpoint.getConfiguration().getUsername());
        assertEquals("secret", endpoint.getConfiguration().getPassword());
        assertEquals("myhost", endpoint.getConfiguration().getHost());
    }

    @Test
    public void testMailComponentWithQuartzScheduler() throws Exception {
        MailConfiguration config = new MailConfiguration();
        config.setUsername("james");
        config.setPassword("secret");

        MailComponent comp = new MailComponent(config);
        comp.setCamelContext(context);

        assertSame(config, comp.getConfiguration());
        assertNull(comp.getContentTypeResolver());

        MailEndpoint endpoint = (MailEndpoint) comp.createEndpoint(
                "imap://myhost?scheduler=quartz&scheduler.cron=0%2F5+*+0-23+%3F+*+*+*&scheduler.timeZone=Europe%2FBerlin");
        assertEquals("james", endpoint.getConfiguration().getUsername());
        assertEquals("secret", endpoint.getConfiguration().getPassword());
        assertEquals("myhost", endpoint.getConfiguration().getHost());

        assertNotNull(endpoint.getScheduler());
        assertEquals("quartz", endpoint.getScheduler());
    }
}
