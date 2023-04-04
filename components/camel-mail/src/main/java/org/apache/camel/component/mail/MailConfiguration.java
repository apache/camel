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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import javax.net.ssl.SSLContext;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.mail.MailConstants.MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_NEVER;
import static org.apache.camel.component.mail.MailConstants.MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_NEVER;

/**
 * Represents the configuration data for communicating over email
 */
@UriParams
public class MailConfiguration implements Cloneable {

    private transient ClassLoader applicationClassLoader;
    private transient Map<Message.RecipientType, String> recipients = new HashMap<>();

    // protocol is implied by component name so it should not be in UriPath
    private transient String protocol;

    @UriPath
    @Metadata(required = true)
    private String host;
    @UriPath
    private int port = -1;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam
    @Metadata(label = "producer")
    private String subject;
    @UriParam
    @Metadata(label = "producer,advanced")
    private JavaMailSender javaMailSender;
    @UriParam(label = "advanced")
    private Session session;
    @UriParam(defaultValue = "true", label = "consumer,advanced")
    private boolean mapMailMessage = true;
    @UriParam(defaultValue = MailConstants.MAIL_DEFAULT_FROM, label = "producer")
    private String from = MailConstants.MAIL_DEFAULT_FROM;
    @UriParam(label = "producer")
    private String to;
    @UriParam(label = "producer")
    private String cc;
    @UriParam(label = "producer")
    private String bcc;
    @UriParam(defaultValue = MailConstants.MAIL_DEFAULT_FOLDER, label = "consumer,advanced")
    private String folderName = MailConstants.MAIL_DEFAULT_FOLDER;
    @UriParam
    @Metadata(label = "consumer")
    private boolean delete;
    @UriParam
    @Metadata(label = "consumer")
    private String copyTo;
    @UriParam
    @Metadata(label = "consumer")
    private String moveTo;
    @UriParam(defaultValue = "true")
    @Metadata(label = "consumer")
    private boolean unseen = true;
    @UriParam(label = "advanced")
    private boolean ignoreUriScheme;
    @UriParam
    @Metadata(label = "producer")
    private String replyTo;
    @UriParam(defaultValue = "-1")
    @Metadata(label = "consumer,advanced")
    private int fetchSize = -1;
    @UriParam(label = "advanced")
    private boolean debugMode;
    @UriParam(defaultValue = "" + MailConstants.MAIL_DEFAULT_CONNECTION_TIMEOUT, label = "advanced")
    private int connectionTimeout = MailConstants.MAIL_DEFAULT_CONNECTION_TIMEOUT;
    @UriParam(defaultValue = "text/plain", label = "advanced")
    private String contentType = "text/plain";
    @UriParam(defaultValue = MailConstants.MAIL_ALTERNATIVE_BODY, label = "advanced")
    private String alternativeBodyHeader = MailConstants.MAIL_ALTERNATIVE_BODY;
    @UriParam(label = "advanced")
    private boolean useInlineAttachments;
    @UriParam(label = "advanced")
    private boolean ignoreUnsupportedCharset;
    @UriParam
    @Metadata(label = "consumer")
    private boolean disconnect;
    @UriParam(defaultValue = "true")
    @Metadata(label = "consumer")
    private boolean closeFolder = true;
    @UriParam(defaultValue = "true")
    @Metadata(label = "consumer")
    private boolean peek = true;
    @UriParam
    @Metadata(label = "consumer")
    private boolean skipFailedMessage;
    @UriParam
    @Metadata(label = "consumer")
    private boolean handleFailedMessage;
    @UriParam(defaultValue = "false")
    @Metadata(label = "consumer")
    private boolean mimeDecodeHeaders;
    @UriParam(label = "consumer")
    private boolean decodeFilename;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "advanced", prefix = "mail.", multiValue = true)
    private Properties additionalJavaMailProperties;
    @UriParam(label = "advanced")
    private AttachmentsContentTransferEncodingResolver attachmentsContentTransferEncodingResolver;
    @UriParam(label = "advanced")
    private Properties javaMailProperties;
    @UriParam(label = "advanced")
    private MailAuthenticator authenticator;
    @UriParam(label = "consumer,advanced")
    private boolean failOnDuplicateFileAttachment;
    @UriParam(label = "consumer,advanced")
    private String generateMissingAttachmentNames = MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_NEVER;
    @UriParam(label = "consumer,advanced")
    private String handleDuplicateAttachmentNames = MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_NEVER;

    public MailConfiguration() {
    }

    public MailConfiguration(CamelContext context) {
        this.applicationClassLoader = context.getApplicationContextClassLoader();
    }

    /**
     * Returns a copy of this configuration
     */
    public MailConfiguration copy() {
        try {
            MailConfiguration copy = (MailConfiguration) clone();
            // must set a new recipients map as clone just reuse the same reference
            copy.recipients = new HashMap<>();
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
                configureProtocol(scheme);
            }
        }

        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            String[] parts = uri.getUserInfo().split(":");
            if (parts.length == 2) {
                setUsername(parts[0]);
                setPassword(parts[1]);
            } else {
                setUsername(userInfo);
            }
        }

        int uriPort = uri.getPort();
        if (uriPort > 0) {
            setPort(uriPort);
        } else if (this.port <= 0) {
            // resolve default port if no port number was provided, and not already configured with a port number
            setPort(MailUtils.getDefaultPortForProtocol(uri.getScheme()));
        }
    }

    protected JavaMailSender createJavaMailSender(CamelContext context) {
        JavaMailSender answer = new DefaultJavaMailSender();

        if (javaMailProperties != null) {
            answer.setJavaMailProperties(javaMailProperties);
        } else {
            // set default properties if none provided
            answer.setJavaMailProperties(createJavaMailProperties(context));
            // add additional properties if provided
            if (additionalJavaMailProperties != null) {
                answer.getJavaMailProperties().putAll(additionalJavaMailProperties);
            }
        }

        if (host != null) {
            answer.setHost(host);
        }
        if (port >= 0) {
            answer.setPort(port);
        }
        if (username != null) {
            answer.setUsername(username);
        }
        if (password != null) {
            answer.setPassword(password);
        }
        if (authenticator != null) {
            answer.setAuthenticator(authenticator);
        }
        if (protocol != null) {
            answer.setProtocol(protocol);
        }
        if (session != null) {
            answer.setSession(session);
            String hostPropertyValue = session.getProperty("mail.smtp.host");
            if (hostPropertyValue != null && !hostPropertyValue.isEmpty()) {
                answer.setHost(hostPropertyValue);
            }
            String portPropertyValue = session.getProperty("mail.smtp.port");
            if (portPropertyValue != null && !portPropertyValue.isEmpty()) {
                answer.setPort(Integer.parseInt(portPropertyValue));
            }
        } else {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            try {
                if (applicationClassLoader != null) {
                    Thread.currentThread().setContextClassLoader(applicationClassLoader);
                }
                // use our authenticator that does no live user interaction but returns the already configured username and password
                Session sessionInstance = Session.getInstance(answer.getJavaMailProperties(),
                        authenticator == null ? new DefaultAuthenticator(getUsername(), getPassword()) : authenticator);
                // sets the debug mode of the underlying mail framework
                sessionInstance.setDebug(debugMode);
                answer.setSession(sessionInstance);
            } finally {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }

        return answer;
    }

    private Properties createJavaMailProperties(CamelContext context) {
        // clone the system properties and set the java mail properties
        Properties properties = (Properties) System.getProperties().clone();
        properties.put("mail." + protocol + ".connectiontimeout", connectionTimeout);
        properties.put("mail." + protocol + ".timeout", connectionTimeout);
        properties.put("mail." + protocol + ".host", host);
        properties.put("mail." + protocol + ".port", Integer.toString(port));
        String pUserName = getPasswordAuthentication().getUserName();
        if (pUserName != null) {
            properties.put("mail." + protocol + ".user", pUserName);
            properties.put("mail.user", pUserName);
            properties.put("mail." + protocol + ".auth", "true");
        } else {
            properties.put("mail." + protocol + ".auth", "false");
        }
        properties.put("mail.transport.protocol", protocol);
        properties.put("mail.store.protocol", protocol);
        properties.put("mail.host", host);

        if (debugMode) {
            // add more debug for the SSL communication as well
            properties.put("javax.net.debug", "all");
        }

        if (sslContextParameters != null && isSecureProtocol()) {
            properties.put("mail." + protocol + ".socketFactory", createSSLContext(context).getSocketFactory());
            properties.put("mail." + protocol + ".socketFactory.fallback", "false");
            properties.put("mail." + protocol + ".socketFactory.port", Integer.toString(port));
        }
        if (sslContextParameters != null && isStartTlsEnabled()) {
            properties.put("mail." + protocol + ".ssl.socketFactory", createSSLContext(context).getSocketFactory());
            properties.put("mail." + protocol + ".ssl.socketFactory.port", Integer.toString(port));
        }

        return properties;
    }

    /**
     * Returns the password authentication from the authenticator or from the parameters user and password.
     */
    public PasswordAuthentication getPasswordAuthentication() {
        // call authenticator so that the authenticator can dynamically determine the password or token
        return authenticator == null
                ? new PasswordAuthentication(username, password) : authenticator.getPasswordAuthentication();
    }

    private SSLContext createSSLContext(CamelContext context) {
        try {
            return sslContextParameters.createSSLContext(context);
        } catch (Exception e) {
            throw new RuntimeCamelException("Error initializing SSLContext.", e);
        }
    }

    /**
     * Is the used protocol to be secure or not
     */
    public boolean isSecureProtocol() {
        return this.protocol.equalsIgnoreCase("smtps") || this.protocol.equalsIgnoreCase("pop3s")
                || this.protocol.equalsIgnoreCase("imaps");
    }

    public boolean isStartTlsEnabled() {
        if (additionalJavaMailProperties != null) {
            return ObjectHelper.equal(additionalJavaMailProperties.getProperty("mail." + protocol + ".starttls.enable"), "true",
                    true)
                    || ObjectHelper.equal(additionalJavaMailProperties.getProperty("mail." + protocol + ".starttls.required"),
                            "true", true);
        }

        return false;
    }

    public String getMailStoreLogInformation() {
        String ssl = "";
        if (isSecureProtocol()) {
            ssl = " (SSL enabled)";
        }

        return protocol + "://" + host + ":" + port + ssl + ", folder=" + folderName;
    }

    // Properties
    // -------------------------------------------------------------------------

    public JavaMailSender getJavaMailSender() {
        return javaMailSender;
    }

    /**
     * To use a custom {@link org.apache.camel.component.mail.JavaMailSender} for sending emails.
     */
    public void setJavaMailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    public String getHost() {
        return host;
    }

    /**
     * The mail server host name
     */
    public void setHost(String host) {
        this.host = host;
    }

    public Properties getJavaMailProperties() {
        return javaMailProperties;
    }

    /**
     * Sets the java mail options. Will clear any default properties and only use the properties provided for this
     * method.
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
     * Sets additional java mail properties, that will append/override any default properties that is set based on all
     * the other options. This is useful if you need to add some special options but want to keep the others as is.
     */
    public void setAdditionalJavaMailProperties(Properties additionalJavaMailProperties) {
        this.additionalJavaMailProperties = additionalJavaMailProperties;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The password for login. See also {@link #setAuthenticator(MailAuthenticator)}.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public MailAuthenticator getAuthenticator() {
        return authenticator;
    }

    /**
     * The authenticator for login. If set then the <code>password</code> and <code>username</code> are ignored. Can be
     * used for tokens which can expire and therefore must be read dynamically.
     */
    public void setAuthenticator(MailAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public String getSubject() {
        return subject;
    }

    /**
     * The Subject of the message being sent. Note: Setting the subject in the header takes precedence over this option.
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getPort() {
        return port;
    }

    /**
     * The port number of the mail server
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol for communicating with the mail server
     */
    public void configureProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Session getSession() {
        return session;
    }

    /**
     * Specifies the mail session that camel should use for all mail interactions. Useful in scenarios where mail
     * sessions are created and managed by some other resource, such as a JavaEE container. When using a custom mail
     * session, then the hostname and port from the mail session will be used (if configured on the session).
     */
    public void setSession(Session session) {
        this.session = session;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The username for login. See also {@link #setAuthenticator(MailAuthenticator)}.
     */
    public void setUsername(String username) {
        this.username = username;
        if (getRecipients().size() == 0) {
            // set default destination to username@host for backwards compatibility
            // can be overridden by URI parameters
            String address = username;
            if (address.indexOf('@') == -1) {
                address += "@" + host;
            }
            setTo(address);
        }
    }

    public String getFrom() {
        return from;
    }

    /**
     * The from email address
     */
    public void setFrom(String from) {
        this.from = from;
    }

    public boolean isDelete() {
        return delete;
    }

    /**
     * Deletes the messages after they have been processed. This is done by setting the DELETED flag on the mail
     * message. If false, the SEEN flag is set instead. As of Camel 2.10 you can override this configuration option by
     * setting a header with the key delete to determine if the mail should be deleted or not.
     */
    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isMapMailMessage() {
        return mapMailMessage;
    }

    /**
     * Specifies whether Camel should map the received mail message to Camel body/headers/attachments. If set to true,
     * the body of the mail message is mapped to the body of the Camel IN message, the mail headers are mapped to IN
     * headers, and the attachments to Camel IN attachment message. If this option is set to false then the IN message
     * contains a raw jakarta.mail.Message. You can retrieve this raw message by calling
     * exchange.getIn().getBody(jakarta.mail.Message.class).
     */
    public void setMapMailMessage(boolean mapMailMessage) {
        this.mapMailMessage = mapMailMessage;
    }

    public String getFolderName() {
        return folderName;
    }

    /**
     * The folder to poll.
     */
    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public boolean isIgnoreUriScheme() {
        return ignoreUriScheme;
    }

    /**
     * Option to let Camel ignore unsupported charset in the local JVM when sending mails. If the charset is unsupported
     * then charset=XXX (where XXX represents the unsupported charset) is removed from the content-type and it relies on
     * the platform default instead.
     */
    public void setIgnoreUriScheme(boolean ignoreUriScheme) {
        this.ignoreUriScheme = ignoreUriScheme;
    }

    public boolean isUnseen() {
        return unseen;
    }

    /**
     * Whether to limit by unseen mails only.
     */
    public void setUnseen(boolean unseen) {
        this.unseen = unseen;
    }

    /**
     * Sets the To email address. Separate multiple email addresses with comma.
     */
    public void setTo(String address) {
        this.to = address;
        recipients.put(Message.RecipientType.TO, address);
    }

    public String getTo() {
        return to;
    }

    /**
     * Sets the CC email address. Separate multiple email addresses with comma.
     */
    public void setCc(String address) {
        this.cc = address;
        recipients.put(Message.RecipientType.CC, address);
    }

    public String getCc() {
        return cc;
    }

    /**
     * Sets the BCC email address. Separate multiple email addresses with comma.
     */
    public void setBcc(String address) {
        this.bcc = address;
        recipients.put(Message.RecipientType.BCC, address);
    }

    public String getBcc() {
        return bcc;
    }

    public Map<Message.RecipientType, String> getRecipients() {
        return recipients;
    }

    public String getReplyTo() {
        return replyTo;
    }

    /**
     * The Reply-To recipients (the receivers of the response mail). Separate multiple email addresses with a comma.
     */
    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    /**
     * Sets the maximum number of messages to consume during a poll. This can be used to avoid overloading a mail
     * server, if a mailbox folder contains a lot of messages. Default value of -1 means no fetch size and all messages
     * will be consumed. Setting the value to 0 is a special corner case, where Camel will not consume any messages at
     * all.
     */
    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Enable debug mode on the underlying mail framework. The SUN Mail framework logs the debug messages to System.out
     * by default.
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * The connection timeout in milliseconds.
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * The mail message content type. Use text/html for HTML mails.
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getAlternativeBodyHeader() {
        return alternativeBodyHeader;
    }

    /**
     * Specifies the key to an IN message header that contains an alternative email body. For example, if you send
     * emails in text/html format and want to provide an alternative mail body for non-HTML email clients, set the
     * alternative mail body with this key as a header.
     */
    public void setAlternativeBodyHeader(String alternativeBodyHeader) {
        this.alternativeBodyHeader = alternativeBodyHeader;
    }

    public boolean isUseInlineAttachments() {
        return useInlineAttachments;
    }

    /**
     * Whether to use disposition inline or attachment.
     */
    public void setUseInlineAttachments(boolean useInlineAttachments) {
        this.useInlineAttachments = useInlineAttachments;
    }

    public boolean isIgnoreUnsupportedCharset() {
        return ignoreUnsupportedCharset;
    }

    /**
     * Option to let Camel ignore unsupported charset in the local JVM when sending mails. If the charset is unsupported
     * then charset=XXX (where XXX represents the unsupported charset) is removed from the content-type and it relies on
     * the platform default instead.
     */
    public void setIgnoreUnsupportedCharset(boolean ignoreUnsupportedCharset) {
        this.ignoreUnsupportedCharset = ignoreUnsupportedCharset;
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    /**
     * Whether the consumer should disconnect after polling. If enabled this forces Camel to connect on each poll.
     */
    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public boolean isCloseFolder() {
        return closeFolder;
    }

    /**
     * Whether the consumer should close the folder after polling. Setting this option to false and having
     * disconnect=false as well, then the consumer keep the folder open between polls.
     */
    public void setCloseFolder(boolean closeFolder) {
        this.closeFolder = closeFolder;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String getCopyTo() {
        return copyTo;
    }

    public String getMoveTo() {
        return moveTo;
    }

    /**
     * After processing a mail message, it can be moved to a mail folder with the given name. You can override this
     * configuration value, with a header with the key moveTo, allowing you to move messages to folder names configured
     * at runtime.
     */
    public void setMoveTo(String moveTo) {
        this.moveTo = moveTo;
    }

    /**
     * After processing a mail message, it can be copied to a mail folder with the given name. You can override this
     * configuration value, with a header with the key copyTo, allowing you to copy messages to folder names configured
     * at runtime.
     */
    public void setCopyTo(String copyTo) {
        this.copyTo = copyTo;
    }

    public boolean isPeek() {
        return peek;
    }

    /**
     * Will mark the jakarta.mail.Message as peeked before processing the mail message. This applies to IMAPMessage
     * messages types only. By using peek the mail will not be eager marked as SEEN on the mail server, which allows us
     * to rollback the mail message if there is an error processing in Camel.
     */
    public void setPeek(boolean peek) {
        this.peek = peek;
    }

    public boolean isSkipFailedMessage() {
        return skipFailedMessage;
    }

    /**
     * If the mail consumer cannot retrieve a given mail message, then this option allows to skip the message and move
     * on to retrieve the next mail message.
     * <p/>
     * The default behavior would be the consumer throws an exception and no mails from the batch would be able to be
     * routed by Camel.
     */
    public void setSkipFailedMessage(boolean skipFailedMessage) {
        this.skipFailedMessage = skipFailedMessage;
    }

    public boolean isHandleFailedMessage() {
        return handleFailedMessage;
    }

    /**
     * If the mail consumer cannot retrieve a given mail message, then this option allows to handle the caused exception
     * by the consumer's error handler. By enable the bridge error handler on the consumer, then the Camel routing error
     * handler can handle the exception instead.
     * <p/>
     * The default behavior would be the consumer throws an exception and no mails from the batch would be able to be
     * routed by Camel.
     */
    public void setHandleFailedMessage(boolean handleFailedMessage) {
        this.handleFailedMessage = handleFailedMessage;
    }

    public AttachmentsContentTransferEncodingResolver getAttachmentsContentTransferEncodingResolver() {
        return attachmentsContentTransferEncodingResolver;
    }

    /**
     * To use a custom AttachmentsContentTransferEncodingResolver to resolve what content-type-encoding to use for
     * attachments.
     */
    public void setAttachmentsContentTransferEncodingResolver(
            AttachmentsContentTransferEncodingResolver attachmentsContentTransferEncodingResolver) {
        this.attachmentsContentTransferEncodingResolver = attachmentsContentTransferEncodingResolver;
    }

    /**
     * This option enables transparent MIME decoding and unfolding for mail headers.
     */
    public void setMimeDecodeHeaders(boolean mimeDecodeHeaders) {
        this.mimeDecodeHeaders = mimeDecodeHeaders;
    }

    public boolean isMimeDecodeHeaders() {
        return mimeDecodeHeaders;
    }

    public boolean isDecodeFilename() {
        return decodeFilename;
    }

    /**
     * If set to true, the MimeUtility.decodeText method will be used to decode the filename. This is similar to setting
     * JVM system property mail.mime.encodefilename.
     */
    public void setDecodeFilename(boolean decodeFilename) {
        this.decodeFilename = decodeFilename;
    }

    public boolean isFailOnDuplicateFileAttachment() {
        return failOnDuplicateFileAttachment;
    }

    /**
     * Whether to fail processing the mail if the mail message contains attachments with duplicate file names. If set to
     * false, then the duplicate attachment is skipped and a WARN is logged. If set to true then an exception is thrown
     * failing to process the mail message.
     */
    public void setFailOnDuplicateFileAttachment(boolean failOnDuplicateFileAttachment) {
        this.failOnDuplicateFileAttachment = failOnDuplicateFileAttachment;
    }

    public String getGenerateMissingAttachmentNames() {
        return generateMissingAttachmentNames;
    }

    /**
     * Set this to 'uuid' to set a UUID for the filename of the attachment if no filename was set
     *
     * @param generateMissingAttachmentNames
     */
    public void setGenerateMissingAttachmentNames(String generateMissingAttachmentNames) {
        this.generateMissingAttachmentNames = generateMissingAttachmentNames;
    }

    public String getHandleDuplicateAttachmentNames() {
        return handleDuplicateAttachmentNames;
    }

    /**
     * Set the strategy to handle duplicate filenames of attachments never: attachments that have a filename which is
     * already present in the attachments will be ignored unless failOnDuplicateFileAttachment is set to true.
     * uuidPrefix: this will prefix the duplicate attachment filenames each with a uuid and underscore
     * (uuid_filename.fileextension). uuidSuffix: this will suffix the duplicate attachment filenames each with a
     * underscore and uuid (filename_uuid.fileextension).
     *
     * @param handleDuplicateAttachmentNames
     */
    public void setHandleDuplicateAttachmentNames(String handleDuplicateAttachmentNames) {
        this.handleDuplicateAttachmentNames = handleDuplicateAttachmentNames;
    }
}
