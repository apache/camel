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

import java.util.Date;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
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
    private MailAuthenticator authenticator;
    // -1 means using the default port to access the service
    private int port = -1;
    private String protocol;
    private Session session;

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public Properties getJavaMailProperties() {
        if (javaMailProperties == null) {
            javaMailProperties = new Properties();
        }
        return javaMailProperties;
    }

    @Override
    public void setJavaMailProperties(Properties javaMailProperties) {
        this.javaMailProperties = javaMailProperties;
    }

    public void addAdditionalJavaMailProperty(String key, String value) {
        getJavaMailProperties().setProperty(key, value);
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public Session getSession() {
        if (session == null) {
            session = Session.getInstance(getJavaMailProperties(),
                    authenticator == null ? new DefaultAuthenticator(username, password) : authenticator);
        }
        return session;
    }

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public MailAuthenticator getAuthenticator() {
        return authenticator;
    }

    @Override
    public void setAuthenticator(MailAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public int getPort() {
        return port;
    }

    @Override
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Returns the password authentication from the authenticator or from the
     * parameters user and password.
     */
    public PasswordAuthentication getPasswordAuthentication() {
        // call authenticator so that the authenticator can dynamically determine the password or token
        return authenticator == null ? new PasswordAuthentication(username, password) : authenticator.getPasswordAuthentication();
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MessagingException {
        Transport transport = getTransport(getSession());
        LOG.debug("Connecting to {}:{}", host, port);
        PasswordAuthentication passwordAuth = getPasswordAuthentication();
        transport.connect(getHost(), getPort(), passwordAuth.getUserName(), passwordAuth.getPassword());
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
