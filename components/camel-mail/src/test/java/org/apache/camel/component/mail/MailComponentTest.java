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

import javax.mail.Message;

import org.apache.camel.Endpoint;
import org.apache.camel.pollconsumer.quartz2.QuartzScheduledPollConsumerScheduler;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class MailComponentTest extends CamelTestSupport {

    @Test
    public void testMailEndpointsAreConfiguredProperlyWhenUsingSmtp() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost:25/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 25, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals(false, config.isDebugMode());
    }

    @Test
    public void testMailEndpointsAreConfiguredProperlyWhenUsingImap() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("imap://james@myhost:143/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "imap", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 143, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals(false, config.isDebugMode());
    }

    @Test
    public void testMailEndpointsAreConfiguredProperlyWhenUsingPop() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("pop3://james@myhost:110/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "pop3", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 110, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals(false, config.isDebugMode());
    }

    @Test
    public void testDefaultSMTPConfiguration() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost?password=secret");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", MailUtils.DEFAULT_PORT_SMTP, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals("from", "camel@localhost", config.getFrom());
        assertEquals("password", "secret", config.getPassword());
        assertEquals(false, config.isDelete());
        assertEquals(false, config.isIgnoreUriScheme());
        assertEquals("fetchSize", -1, config.getFetchSize());
        assertEquals("contentType", "text/plain", config.getContentType());
        assertEquals("unseen", true, config.isUnseen());
        assertEquals(false, config.isDebugMode());
        assertEquals(MailConstants.MAIL_DEFAULT_CONNECTION_TIMEOUT, config.getConnectionTimeout());
        assertEquals(false, config.isSecureProtocol());
        assertEquals("smtp://myhost:25, folder=INBOX", config.getMailStoreLogInformation());
    }

    @Test
    public void testDefaultSMTPSConfiguration() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtps://james@myhost?password=secret");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtps", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", MailUtils.DEFAULT_PORT_SMTPS, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals("from", "camel@localhost", config.getFrom());
        assertEquals("password", "secret", config.getPassword());
        assertEquals(false, config.isDelete());
        assertEquals(false, config.isIgnoreUriScheme());
        assertEquals("fetchSize", -1, config.getFetchSize());
        assertEquals("contentType", "text/plain", config.getContentType());
        assertEquals("unseen", true, config.isUnseen());
        assertEquals(false, config.isDebugMode());
        assertEquals(MailConstants.MAIL_DEFAULT_CONNECTION_TIMEOUT, config.getConnectionTimeout());
        assertEquals(true, config.isSecureProtocol());
        assertEquals("smtps://myhost:465 (SSL enabled), folder=INBOX", config.getMailStoreLogInformation());
    }

    @Test
    public void testDebugMode() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost?password=secret&debugMode=true");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals(true, config.isDebugMode());
    }

    @Test
    public void testConnectionTimeout() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost?password=secret&connectionTimeout=2500");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals(2500, config.getConnectionTimeout());
    }

    @Test
    public void testDummyTrustManager() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost?password=secret&dummyTrustManager=true");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals(true, config.isDummyTrustManager());
        assertEquals(false, config.isSecureProtocol());
    }

    @Test
    public void testDummyTrustManagerSecure() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtps://james@myhost?password=secret&dummyTrustManager=true");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals(true, config.isDummyTrustManager());
        assertEquals(true, config.isSecureProtocol());
        assertEquals("smtps://myhost:465 (SSL enabled using DummyTrustManager), folder=INBOX", config.getMailStoreLogInformation());
    }

    @Test
    public void testDefaultPOP3Configuration() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("pop3://james@myhost?password=secret");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "pop3", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", MailUtils.DEFAULT_PORT_POP3, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals("from", "camel@localhost", config.getFrom());
        assertEquals("password", "secret", config.getPassword());
        assertEquals(false, config.isDelete());
        assertEquals(false, config.isIgnoreUriScheme());
        assertEquals("fetchSize", -1, config.getFetchSize());
        assertEquals("contentType", "text/plain", config.getContentType());
        assertEquals("unseen", true, config.isUnseen());
        assertEquals(false, config.isDebugMode());
    }

    @Test
    public void testDefaultIMAPConfiguration() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("imap://james@myhost?password=secret");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "imap", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", MailUtils.DEFAULT_PORT_IMAP, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals("from", "camel@localhost", config.getFrom());
        assertEquals("password", "secret", config.getPassword());
        assertEquals(false, config.isDelete());
        assertEquals(false, config.isIgnoreUriScheme());
        assertEquals("fetchSize", -1, config.getFetchSize());
        assertEquals("contentType", "text/plain", config.getContentType());
        assertEquals("unseen", true, config.isUnseen());
        assertEquals(false, config.isDebugMode());
    }

    @Test
    public void testManyConfigurations() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost:30/subject?password=secret"
            + "&from=me@camelriders.org&delete=true&folderName=riders"
            + "&contentType=text/html&unseen=false");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 30, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "riders", config.getFolderName());
        assertEquals("from", "me@camelriders.org", config.getFrom());
        assertEquals("password", "secret", config.getPassword());
        assertEquals(true, config.isDelete());
        assertEquals(false, config.isIgnoreUriScheme());
        assertEquals("fetchSize", -1, config.getFetchSize());
        assertEquals("unseen", false, config.isUnseen());
        assertEquals("contentType", "text/html", config.getContentType());
        assertEquals(false, config.isDebugMode());
    }

    @Test
    public void testTo() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost:25/?password=secret&to=someone@outthere.com&folderName=XXX");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 25, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "someone@outthere.com", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "XXX", config.getFolderName());
        assertEquals("from", "camel@localhost", config.getFrom());
        assertEquals("password", "secret", config.getPassword());
        assertEquals(false, config.isDelete());
        assertEquals(false, config.isIgnoreUriScheme());
        assertEquals("fetchSize", -1, config.getFetchSize());
        assertEquals(false, config.isDebugMode());
    }

    @Test
    public void testNoUserInfoButUsername() {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://myhost:25/?password=secret&username=james");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 25, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals("from", "camel@localhost", config.getFrom());
        assertEquals("password", "secret", config.getPassword());
        assertEquals(false, config.isDelete());
        assertEquals(false, config.isIgnoreUriScheme());
        assertEquals("fetchSize", -1, config.getFetchSize());
        assertEquals(false, config.isDebugMode());
    }

    @Test
    public void testMailEndpointsWithFetchSize() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("pop3://james@myhost?fetchSize=5");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "pop3", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 110, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
        assertEquals("getRecipients().get(Message.RecipientType.TO)", "james@myhost", config.getRecipients().get(Message.RecipientType.TO));
        assertEquals("folder", "INBOX", config.getFolderName());
        assertEquals("fetchSize", 5, config.getFetchSize());
        assertEquals(false, config.isDebugMode());
    }

    @Test
    public void testSMTPEndpointWithSubjectOption() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://myhost:25?subject=hello");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 25, config.getPort());
        assertEquals("getSubject()", "hello", config.getSubject());
        assertEquals(false, config.isDebugMode());
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

        MailEndpoint endpoint = (MailEndpoint)comp.createEndpoint("imap://myhost?scheduler=quartz2&scheduler.cron=0%2F5+*+0-23+%3F+*+*+*&scheduler.timeZone=Europe%2FBerlin");
        assertEquals("james", endpoint.getConfiguration().getUsername());
        assertEquals("secret", endpoint.getConfiguration().getPassword());
        assertEquals("myhost", endpoint.getConfiguration().getHost());

        assertNotNull("Scheduler not set", endpoint.getScheduler());
        assertTrue("Wrong scheduler class", endpoint.getScheduler() instanceof QuartzScheduledPollConsumerScheduler);
    }
}

