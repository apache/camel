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

import java.util.Date;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link JavaMailSender} which uses the JDK Mail API.
 */
public class DefaultJavaMailSender implements JavaMailSender {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultJavaMailSender.class);

    private Properties javaMailProperties;
    private String host;
    private String username;
    private String password;
    // -1 means using the default port to access the service
    private int port = -1;
    private String protocol;
    private Session session;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Properties getJavaMailProperties() {
        if (javaMailProperties == null) {
            javaMailProperties = new Properties();
        }
        return javaMailProperties;
    }

    public void setJavaMailProperties(Properties javaMailProperties) {
        this.javaMailProperties = javaMailProperties;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Session getSession() {
        if (session == null) {
            session = Session.getInstance(getJavaMailProperties(), new DefaultAuthenticator(username, password));
        }
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MessagingException {
        Transport transport = getTransport(getSession());
        LOG.debug("Connecting to {}:{}", host, port);
        transport.connect(getHost(), getPort(), getUsername(), getPassword());
        try {
            if (mimeMessage.getSentDate() == null) {
                mimeMessage.setSentDate(new Date());
            }
            String messageId = mimeMessage.getMessageID();
            mimeMessage.saveChanges();
            if (messageId != null) {
                // preserve explicitly specified message id, as it may be lost on save
                mimeMessage.setHeader("Message-ID", messageId);
            }
            LOG.debug("Sending MimeMessage: {} using host: {}", mimeMessage, host);
            transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients());
        } finally {
            try {
                transport.close();
            } catch (MessagingException e) {
                LOG.warn("Error closing transport to host " + host + ". This exception will be ignored.", e);
            }
        }
    }

    /**
     * Strategy to get the {@link Transport} from the mail {@link Session}.
     */
    protected Transport getTransport(Session session) throws NoSuchProviderException {
        return session.getTransport(getProtocol());
    }

}
