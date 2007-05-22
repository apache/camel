/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.camel.util.ObjectHelper;

import javax.mail.Session;
import javax.mail.Message;
import javax.mail.Address;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;
import java.util.Properties;

/**
 * @version $Revision: 1.1 $
 */
public class MailComponentTest extends ContextTestSupport {

    public void testMailEndpointsAreConfiguredProperlyWhenUsingSmtp() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("smtp://james@myhost:30/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "smtp", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 30, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
    }

    public void testMailEndpointsAreConfiguredProperlyWhenUsingImap() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("imap://james@myhost:30/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "imap", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 30, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
    }

    public void testMailEndpointsAreConfiguredProperlyWhenUsingPop() throws Exception {
        MailEndpoint endpoint = resolveMandatoryEndpoint("pop3://james@myhost:30/subject");
        MailConfiguration config = endpoint.getConfiguration();
        assertEquals("getProtocol()", "pop3", config.getProtocol());
        assertEquals("getHost()", "myhost", config.getHost());
        assertEquals("getPort()", 30, config.getPort());
        assertEquals("getUsername()", "james", config.getUsername());
    }


    @Override
    protected MailEndpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = super.resolveMandatoryEndpoint(uri);
        return assertIsInstanceOf(MailEndpoint.class, endpoint);
    }
}
