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
import java.util.Properties;

import javax.mail.Session;

import org.apache.camel.RuntimeCamelException;

/**
 * Represents the configuration data for communicating over email
 *
 * @version $Revision$
 */
public class MailConfiguration implements Cloneable {

    public static final String DEFAULT_FOLDER_NAME = "INBOX";
    public static final String DEFAULT_FROM = "camel@localhost";

    private Properties javaMailProperties;
    private String protocol;
    private String host;
    private int port = -1;
    private String username;
    private String password;
    private Session session;
    private String defaultEncoding;
    private String from = DEFAULT_FROM;
    private String destination;
    private String folderName = DEFAULT_FOLDER_NAME;
    private boolean deleteProcessedMessages = true;
    private boolean ignoreUriScheme;
    private boolean processOnlyUnseenMessages;

    public MailConfiguration() {
    }

    /**
     * Returns a copy of this configuration
     */
    public MailConfiguration copy() {
        try {
            return (MailConfiguration)clone();
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

            // set default destination to userInfo@host for backwards compatibility
            // can be overridden by URI parameters
            setDestination(userInfo + "@" + host);
        }

        int port = uri.getPort();
        if (port >= 0) {
            setPort(port);
        } else {
            // resolve default port if no port number was provided
            setPort(MailUtils.getDefaultPortForProtocol(uri.getScheme()));
        }
    }

    public JavaMailConnection createJavaMailConnection(MailEndpoint mailEndpoint) {
        JavaMailConnection answer = new JavaMailConnection();
        if (defaultEncoding != null) {
            answer.setDefaultEncoding(defaultEncoding);
        }
        if (host != null) {
            answer.setHost(host);
        }
        if (javaMailProperties != null) {
            answer.setJavaMailProperties(javaMailProperties);
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
        }
        if (username != null) {
            answer.setUsername(username);
        }
        return answer;
    }

    // Properties
    // -------------------------------------------------------------------------

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

    public void setJavaMailProperties(Properties javaMailProperties) {
        this.javaMailProperties = javaMailProperties;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
        if (destination == null) {
            // set default destination to username@host for backwards compatibility
            // can be overridden by URI parameters
            setDestination(username + "@" + host);
        }
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public boolean isDeleteProcessedMessages() {
        return deleteProcessedMessages;
    }

    public void setDeleteProcessedMessages(boolean deleteProcessedMessages) {
        this.deleteProcessedMessages = deleteProcessedMessages;
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

    public boolean isProcessOnlyUnseenMessages() {
        return processOnlyUnseenMessages;
    }

    public void setProcessOnlyUnseenMessages(boolean processOnlyUnseenMessages) {
        this.processOnlyUnseenMessages = processOnlyUnseenMessages;
    }
}
