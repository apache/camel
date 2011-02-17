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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;

import org.apache.camel.RuntimeCamelException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Represents the configuration data for communicating over email
 *
 * @version 
 */
public class MailConfiguration implements Cloneable {   

    private JavaMailSender javaMailSender;
    private Properties javaMailProperties;
    private Properties additionalJavaMailProperties;
    private String protocol;
    private String host;
    private int port = -1;
    private String username;
    private String password;
    private String subject;
    private Session session;
    private String defaultEncoding;
    private String from = MailConstants.MAIL_DEFAULT_FROM;
    private String folderName = MailConstants.MAIL_DEFAULT_FOLDER;
    private boolean delete;
    private boolean unseen = true;
    private boolean ignoreUriScheme;
    private Map<Message.RecipientType, String> recipients = new HashMap<Message.RecipientType, String>();
    private int fetchSize = -1;
    private boolean debugMode;
    private long connectionTimeout = MailConstants.MAIL_DEFAULT_CONNECTION_TIMEOUT;
    private boolean dummyTrustManager;
    private String contentType = "text/plain";
    private String alternativeBodyHeader = MailConstants.MAIL_ALTERNATIVE_BODY;
    private boolean useInlineAttachments;
    private boolean ignoreUnsupportedCharset;

    public MailConfiguration() {
    }

    /**
     * Returns a copy of this configuration
     */
    public MailConfiguration copy() {
        try {
            MailConfiguration copy = (MailConfiguration) clone();
            // must set a new recipients map as clone just reuse the same reference
            copy.recipients = new HashMap<Message.RecipientType, String>();
            copy.recipients.putAll(this.recipients);
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void configure(URI uri) {
        String value = uri.getHost();
        if (value != null) {
            setHost(value);
        }

        if (!isIgnoreUriScheme()) {
            String scheme = uri.getScheme();
            if (scheme != null) {
                setProtocol(scheme);
            }
        }

        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            setUsername(userInfo);
        }

        int port = uri.getPort();
        if (port > 0) {
            setPort(port);
        } else if (port <= 0 && this.port <= 0) {
            // resolve default port if no port number was provided, and not already configured with a port number
            setPort(MailUtils.getDefaultPortForProtocol(uri.getScheme()));
        }
    }

    protected JavaMailSenderImpl createJavaMailSender() {
        JavaMailSenderImpl answer = new JavaMailSenderImpl();

        // sets the debug mode of the underlying mail framework
        answer.getSession().setDebug(debugMode);

        if (javaMailProperties != null) {
            answer.setJavaMailProperties(javaMailProperties);
        } else {
            // set default properties if none provided
            answer.setJavaMailProperties(createJavaMailProperties());
            // add additional properties if provided
            if (additionalJavaMailProperties != null) {
                answer.getJavaMailProperties().putAll(additionalJavaMailProperties);
            }
        }

        if (defaultEncoding != null) {
            answer.setDefaultEncoding(defaultEncoding);
        }
        if (host != null) {
            answer.setHost(host);
        }
        if (port >= 0) {
            answer.setPort(port);
        }
        if (password != null) {
            answer.setPassword(password);
        }
        if (protocol != null) {
            answer.setProtocol(protocol);
        }
        if (session != null) {
            answer.setSession(session);
        } else {
            // use our authenticator that does no live user interaction but returns the already configured username and password
            Session session;
            try {
                session = Session.getDefaultInstance(answer.getJavaMailProperties(), getAuthenticator());
            } catch (Throwable t) {
                // fallback as default instance may not be allowed on some systems
                session = Session.getInstance(answer.getJavaMailProperties(), getAuthenticator());
            }
            answer.setSession(session);
        }
        if (username != null) {
            answer.setUsername(username);
        }

        return answer;
    }

    private Properties createJavaMailProperties() {
        // clone the system properties and set the java mail properties
        Properties properties = (Properties)System.getProperties().clone();
        properties.put("mail." + protocol + ".connectiontimeout", connectionTimeout);
        properties.put("mail." + protocol + ".timeout", connectionTimeout);
        properties.put("mail." + protocol + ".host", host);
        properties.put("mail." + protocol + ".port", "" + port);
        if (username != null) {
            properties.put("mail." + protocol + ".user", username);
            properties.put("mail.user", username);
            properties.put("mail." + protocol + ".auth", "true");
        } else {
            properties.put("mail." + protocol + ".auth", "false");
        }
        properties.put("mail." + protocol + ".rsetbeforequit", "true");
        properties.put("mail.transport.protocol", protocol);
        properties.put("mail.store.protocol", protocol);
        properties.put("mail.host", host);

        if (debugMode) {
            // add more debug for the SSL communication as well
            properties.put("javax.net.debug", "all");
        }

        if (dummyTrustManager && isSecureProtocol()) {
            // set the custom SSL properties
            properties.put("mail." + protocol + ".socketFactory.class", "org.apache.camel.component.mail.security.DummySSLSocketFactory");
            properties.put("mail." + protocol + ".socketFactory.fallback", "false");
            properties.put("mail." + protocol + ".socketFactory.port", "" + port);
        }

        return properties;
    }

    /**
     * Is the used protocol to be secure or not
     */
    public boolean isSecureProtocol() {
        return this.protocol.equalsIgnoreCase("smtps") || this.protocol.equalsIgnoreCase("pop3s")
               || this.protocol.equalsIgnoreCase("imaps");
    }

    /**
     * Returns an authenticator object for use in sessions
     */
    public Authenticator getAuthenticator() {
        return new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(getUsername(), getPassword());
            }
        };
    }

    public String getMailStoreLogInformation() {
        String ssl = "";
        if (isSecureProtocol()) {
            ssl = " (SSL enabled" + (dummyTrustManager ? " using DummyTrustManager)" : ")");
        }

        return protocol + "://" + host + ":" + port + ssl + ", folder=" + folderName;
    }

    // Properties
    // -------------------------------------------------------------------------

    public JavaMailSender getJavaMailSender() {
        return javaMailSender;
    }

    public void setJavaMailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public void setDefaultEncoding(String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Properties getJavaMailProperties() {
        return javaMailProperties;
    }

    /**
     * Sets the java mail options. Will clear any default properties and only use the properties
     * provided for this method.
     */
    public void setJavaMailProperties(Properties javaMailProperties) {
        this.javaMailProperties = javaMailProperties;
    }

    public Properties getAdditionalJavaMailProperties() {
        if (additionalJavaMailProperties == null) {
            additionalJavaMailProperties = new Properties();
        }
        return additionalJavaMailProperties;
    }

    /**
     * Sets additional java mail properties, that will append/override any default properties
     * that is set based on all the other options. This is useful if you need to add some
     * special options but want to keep the others as is.
     */
    public void setAdditionalJavaMailProperties(Properties additionalJavaMailProperties) {
        this.additionalJavaMailProperties = additionalJavaMailProperties;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
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

    public Session getSession() {
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
        if (getRecipients().size() == 0) {
            // set default destination to username@host for backwards compatibility
            // can be overridden by URI parameters
            String address = username;
            if (address.indexOf("@") == -1) {
                address += "@" + host;
            }
            setTo(address);
        }
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public boolean isIgnoreUriScheme() {
        return ignoreUriScheme;
    }

    public void setIgnoreUriScheme(boolean ignoreUriScheme) {
        this.ignoreUriScheme = ignoreUriScheme;
    }

    public boolean isUnseen() {
        return unseen;
    }

    public void setUnseen(boolean unseen) {
        this.unseen = unseen;
    }

    /**
     * Sets the <tt>To</tt> email address. Separate multiple email addresses with comma.
     */
    public void setTo(String address) {
        recipients.put(Message.RecipientType.TO, address);
    }

    /**
     * Sets the <tt>CC</tt> email address. Separate multiple email addresses with comma.
     */
    public void setCC(String address) {
        recipients.put(Message.RecipientType.CC, address);
    }

    /**
     * Sets the <tt>BCC</tt> email address. Separate multiple email addresses with comma.
     */
    public void setBCC(String address) {
        recipients.put(Message.RecipientType.BCC, address);
    }

    public Map<Message.RecipientType, String> getRecipients() {
        return recipients;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isDummyTrustManager() {
        return dummyTrustManager;
    }

    public void setDummyTrustManager(boolean dummyTrustManager) {
        this.dummyTrustManager = dummyTrustManager;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getAlternativeBodyHeader() {
        return alternativeBodyHeader;
    }

    public void setAlternativeBodyHeader(String alternativeBodyHeader) {
        this.alternativeBodyHeader = alternativeBodyHeader;
    }

    public boolean isUseInlineAttachments() {
        return useInlineAttachments;
    }

    public void setUseInlineAttachments(boolean useInlineAttachments) {
        this.useInlineAttachments = useInlineAttachments;
    }

    public boolean isIgnoreUnsupportedCharset() {
        return ignoreUnsupportedCharset;
    }

    public void setIgnoreUnsupportedCharset(boolean ignoreUnsupportedCharset) {
        this.ignoreUnsupportedCharset = ignoreUnsupportedCharset;
    }
}
