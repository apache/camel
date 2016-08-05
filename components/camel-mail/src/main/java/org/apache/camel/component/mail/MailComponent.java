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
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.search.SearchTerm;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * Component for JavaMail.
 *
 * @version
 */
public class MailComponent extends UriEndpointComponent {
    private MailConfiguration configuration;
    private ContentTypeResolver contentTypeResolver;

    public MailComponent() {
        super(MailEndpoint.class);
    }

    public MailComponent(MailConfiguration configuration) {
        super(MailEndpoint.class);
        this.configuration = configuration;
    }

    public MailComponent(CamelContext context) {
        super(context, MailEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        URI url = new URI(uri);

        // must use copy as each endpoint can have different options
        MailConfiguration config = getConfiguration().copy();

        // only configure if we have a url with a known protocol
        config.configure(url);
        configureAdditionalJavaMailProperties(config, parameters);

        MailEndpoint endpoint = new MailEndpoint(uri, this, config);
        endpoint.setContentTypeResolver(contentTypeResolver);
        setProperties(endpoint.getConfiguration(), parameters);
        setProperties(endpoint, parameters);

        Map<String, Object> sstParams = IntrospectionSupport.extractProperties(parameters, "searchTerm.");
        if (!sstParams.isEmpty()) {
            // use SimpleSearchTerm as POJO to store the configuration and then convert that to the actual SearchTerm
            SimpleSearchTerm sst = new SimpleSearchTerm();
            setProperties(sst, sstParams);
            SearchTerm st = MailConverters.toSearchTerm(sst, getCamelContext().getTypeConverter());
            endpoint.setSearchTerm(st);
        }

        // sanity check that we know the mail server
        ObjectHelper.notEmpty(config.getHost(), "host");
        ObjectHelper.notEmpty(config.getProtocol(), "protocol");

        return endpoint;
    }

    private void configureAdditionalJavaMailProperties(MailConfiguration config, Map<String, Object> parameters) {
        // we cannot remove while iterating, as we will get a modification exception
        Set<Object> toRemove = new HashSet<Object>();

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey().toString().startsWith("mail.")) {
                config.getAdditionalJavaMailProperties().put(entry.getKey(), entry.getValue());
                toRemove.add(entry.getKey());
            }
        }

        for (Object key : toRemove) {
            parameters.remove(key);
        }
    }

    public MailConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new MailConfiguration(getCamelContext());
        }
        return configuration;
    }

    /**
     * Sets the Mail configuration. Properties of the shared configuration can also be set individually.
     *
     * @param configuration the configuration to use by default for endpoints
     */
    public void setConfiguration(MailConfiguration configuration) {
        this.configuration = configuration;
    }

    public ContentTypeResolver getContentTypeResolver() {
        return contentTypeResolver;
    }

    /**
     * Resolver to determine Content-Type for file attachments.
     */
    public void setContentTypeResolver(ContentTypeResolver contentTypeResolver) {
        this.contentTypeResolver = contentTypeResolver;
    }

    /**
     * Is the used protocol to be secure or not
     */
    public boolean isSecureProtocol() {
        return getConfiguration().isSecureProtocol();
    }

    public boolean isStartTlsEnabled() {
        return getConfiguration().isStartTlsEnabled();
    }

    public String getMailStoreLogInformation() {
        return getConfiguration().getMailStoreLogInformation();
    }

    public JavaMailSender getJavaMailSender() {
        return getConfiguration().getJavaMailSender();
    }

    /**
     * To use a custom {@link JavaMailSender} for sending emails.
     * @param javaMailSender
     */
    public void setJavaMailSender(JavaMailSender javaMailSender) {
        getConfiguration().setJavaMailSender(javaMailSender);
    }

    public String getHost() {
        return getConfiguration().getHost();
    }

    /**
     * The mail server host name
     * @param host
     */
    public void setHost(String host) {
        getConfiguration().setHost(host);
    }

    public Properties getJavaMailProperties() {
        return getConfiguration().getJavaMailProperties();
    }

    /**
     * Sets the java mail options. Will clear any default properties and only use the properties
     * provided for this method.
     * @param javaMailProperties
     */
    public void setJavaMailProperties(Properties javaMailProperties) {
        getConfiguration().setJavaMailProperties(javaMailProperties);
    }

    public Properties getAdditionalJavaMailProperties() {
        return getConfiguration().getAdditionalJavaMailProperties();
    }

    /**
     * Sets additional java mail properties, that will append/override any default properties
     * that is set based on all the other options. This is useful if you need to add some
     * special options but want to keep the others as is.
     * @param additionalJavaMailProperties
     */
    public void setAdditionalJavaMailProperties(Properties additionalJavaMailProperties) {
        getConfiguration().setAdditionalJavaMailProperties(additionalJavaMailProperties);
    }

    public String getPassword() {
        return getConfiguration().getPassword();
    }

    /**
     * The password for login
     * @param password
     */
    public void setPassword(String password) {
        getConfiguration().setPassword(password);
    }

    public String getSubject() {
        return getConfiguration().getSubject();
    }

    /**
     * The Subject of the message being sent. Note: Setting the subject in the header takes precedence over this option.
     * @param subject
     */
    public void setSubject(String subject) {
        getConfiguration().setSubject(subject);
    }

    public int getPort() {
        return getConfiguration().getPort();
    }

    /**
     * The port number of the mail server
     * @param port
     */
    public void setPort(int port) {
        getConfiguration().setPort(port);
    }

    public String getProtocol() {
        return getConfiguration().getProtocol();
    }

    /**
     * The protocol for communicating with the mail server
     * @param protocol
     */
    public void setProtocol(String protocol) {
        getConfiguration().setProtocol(protocol);
    }

    public Session getSession() {
        return getConfiguration().getSession();
    }

    /**
     * Specifies the mail session that camel should use for all mail interactions. Useful in scenarios where
     * mail sessions are created and managed by some other resource, such as a JavaEE container.
     * If this is not specified, Camel automatically creates the mail session for you.
     * @param session
     */
    public void setSession(Session session) {
        getConfiguration().setSession(session);
    }

    public String getUsername() {
        return getConfiguration().getUsername();
    }

    /**
     * The username for login
     * @param username
     */
    public void setUsername(String username) {
        getConfiguration().setUsername(username);
    }

    public String getFrom() {
        return getConfiguration().getFrom();
    }

    /**
     * The from email address
     * @param from
     */
    public void setFrom(String from) {
        getConfiguration().setFrom(from);
    }

    public boolean isDelete() {
        return getConfiguration().isDelete();
    }

    /**
     * Deletes the messages after they have been processed. This is done by setting the DELETED flag on the mail message.
     * If false, the SEEN flag is set instead. As of Camel 2.10 you can override this configuration option by setting a
     * header with the key delete to determine if the mail should be deleted or not.
     * @param delete
     */
    public void setDelete(boolean delete) {
        getConfiguration().setDelete(delete);
    }

    public boolean isMapMailMessage() {
        return getConfiguration().isMapMailMessage();
    }

    /**
     * Specifies whether Camel should map the received mail message to Camel body/headers.
     * If set to true, the body of the mail message is mapped to the body of the Camel IN message and the mail headers are mapped to IN headers.
     * If this option is set to false then the IN message contains a raw javax.mail.Message.
     * You can retrieve this raw message by calling exchange.getIn().getBody(javax.mail.Message.class).
     * @param mapMailMessage
     */
    public void setMapMailMessage(boolean mapMailMessage) {
        getConfiguration().setMapMailMessage(mapMailMessage);
    }

    public String getFolderName() {
        return getConfiguration().getFolderName();
    }

    /**
     * The folder to poll.
     * @param folderName
     */
    public void setFolderName(String folderName) {
        getConfiguration().setFolderName(folderName);
    }

    public boolean isIgnoreUriScheme() {
        return getConfiguration().isIgnoreUriScheme();
    }

    /**
     * Option to let Camel ignore unsupported charset in the local JVM when sending mails. If the charset is unsupported
     * then charset=XXX (where XXX represents the unsupported charset) is removed from the content-type and it relies on the platform default instead.
     * @param ignoreUriScheme
     */
    public void setIgnoreUriScheme(boolean ignoreUriScheme) {
        getConfiguration().setIgnoreUriScheme(ignoreUriScheme);
    }

    public boolean isUnseen() {
        return getConfiguration().isUnseen();
    }

    /**
     * Whether to limit by unseen mails only.
     * @param unseen
     */
    public void setUnseen(boolean unseen) {
        getConfiguration().setUnseen(unseen);
    }

    /**
     * Sets the <tt>To</tt> email address. Separate multiple email addresses with comma.
     * @param address
     */
    public void setTo(String address) {
        getConfiguration().setTo(address);
    }

    public String getTo() {
        return getConfiguration().getTo();
    }

    /**
     * Sets the <tt>CC</tt> email address. Separate multiple email addresses with comma.
     * @param address
     */
    public void setCc(String address) {
        getConfiguration().setCc(address);
    }

    public String getCc() {
        return getConfiguration().getCc();
    }

    /**
     * Sets the <tt>BCC</tt> email address. Separate multiple email addresses with comma.
     * @param address
     */
    public void setBcc(String address) {
        getConfiguration().setBcc(address);
    }

    public String getBcc() {
        return getConfiguration().getBcc();
    }

    public Map<Message.RecipientType, String> getRecipients() {
        return getConfiguration().getRecipients();
    }

    public String getReplyTo() {
        return getConfiguration().getReplyTo();
    }

    /**
     * The Reply-To recipients (the receivers of the response mail). Separate multiple email addresses with a comma.
     * @param replyTo
     */
    public void setReplyTo(String replyTo) {
        getConfiguration().setReplyTo(replyTo);
    }

    public int getFetchSize() {
        return getConfiguration().getFetchSize();
    }

    /**
     * Sets the maximum number of messages to consume during a poll. This can be used to avoid overloading a mail server,
     * if a mailbox folder contains a lot of messages. Default value of -1 means no fetch size and all messages will be consumed.
     * Setting the value to 0 is a special corner case, where Camel will not consume any messages at all.
     * @param fetchSize
     */
    public void setFetchSize(int fetchSize) {
        getConfiguration().setFetchSize(fetchSize);
    }

    public boolean isDebugMode() {
        return getConfiguration().isDebugMode();
    }

    /**
     * Enable debug mode on the underlying mail framework. The SUN Mail framework logs the debug messages to System.out by default.
     * @param debugMode
     */
    public void setDebugMode(boolean debugMode) {
        getConfiguration().setDebugMode(debugMode);
    }

    public long getConnectionTimeout() {
        return getConfiguration().getConnectionTimeout();
    }

    /**
     * The connection timeout in milliseconds.
     * @param connectionTimeout
     */
    public void setConnectionTimeout(int connectionTimeout) {
        getConfiguration().setConnectionTimeout(connectionTimeout);
    }

    public boolean isDummyTrustManager() {
        return getConfiguration().isDummyTrustManager();
    }

    /**
     * To use a dummy security setting for trusting all certificates. Should only be used for development mode, and not production.
     * @param dummyTrustManager
     */
    public void setDummyTrustManager(boolean dummyTrustManager) {
        getConfiguration().setDummyTrustManager(dummyTrustManager);
    }

    public String getContentType() {
        return getConfiguration().getContentType();
    }

    /**
     * The mail message content type. Use text/html for HTML mails.
     * @param contentType
     */
    public void setContentType(String contentType) {
        getConfiguration().setContentType(contentType);
    }

    public String getAlternativeBodyHeader() {
        return getConfiguration().getAlternativeBodyHeader();
    }

    /**
     * Specifies the key to an IN message header that contains an alternative email body.
     * For example, if you send emails in text/html format and want to provide an alternative mail body for
     * non-HTML email clients, set the alternative mail body with this key as a header.
     * @param alternativeBodyHeader
     */
    public void setAlternativeBodyHeader(String alternativeBodyHeader) {
        getConfiguration().setAlternativeBodyHeader(alternativeBodyHeader);
    }

    public boolean isUseInlineAttachments() {
        return getConfiguration().isUseInlineAttachments();
    }

    /**
     * Whether to use disposition inline or attachment.
     * @param useInlineAttachments
     */
    public void setUseInlineAttachments(boolean useInlineAttachments) {
        getConfiguration().setUseInlineAttachments(useInlineAttachments);
    }

    public boolean isIgnoreUnsupportedCharset() {
        return getConfiguration().isIgnoreUnsupportedCharset();
    }

    /**
     * Option to let Camel ignore unsupported charset in the local JVM when sending mails.
     * If the charset is unsupported then charset=XXX (where XXX represents the unsupported charset)
     * is removed from the content-type and it relies on the platform default instead.
     * @param ignoreUnsupportedCharset
     */
    public void setIgnoreUnsupportedCharset(boolean ignoreUnsupportedCharset) {
        getConfiguration().setIgnoreUnsupportedCharset(ignoreUnsupportedCharset);
    }

    public boolean isDisconnect() {
        return getConfiguration().isDisconnect();
    }

    /**
     * Whether the consumer should disconnect after polling. If enabled this forces Camel to connect on each poll.
     * @param disconnect
     */
    public void setDisconnect(boolean disconnect) {
        getConfiguration().setDisconnect(disconnect);
    }

    public boolean isCloseFolder() {
        return getConfiguration().isCloseFolder();
    }

    /**
     * Whether the consumer should close the folder after polling. Setting this option to false and having disconnect=false as well,
     * then the consumer keep the folder open between polls.
     * @param closeFolder
     */
    public void setCloseFolder(boolean closeFolder) {
        getConfiguration().setCloseFolder(closeFolder);
    }

    public SSLContextParameters getSslContextParameters() {
        return getConfiguration().getSslContextParameters();
    }

    /**
     * To configure security using SSLContextParameters.
     * @param sslContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        getConfiguration().setSslContextParameters(sslContextParameters);
    }

    public String getCopyTo() {
        return getConfiguration().getCopyTo();
    }

    /**
     * After processing a mail message, it can be copied to a mail folder with the given name.
     * You can override this configuration value, with a header with the key copyTo, allowing you to copy messages
     * to folder names configured at runtime.
     * @param copyTo
     */
    public void setCopyTo(String copyTo) {
        getConfiguration().setCopyTo(copyTo);
    }

    public boolean isPeek() {
        return getConfiguration().isPeek();
    }

    /**
     * Will mark the javax.mail.Message as peeked before processing the mail message.
     * This applies to IMAPMessage messages types only. By using peek the mail will not be eager marked as SEEN on
     * the mail server, which allows us to rollback the mail message if there is an error processing in Camel.
     * @param peek
     */
    public void setPeek(boolean peek) {
        getConfiguration().setPeek(peek);
    }

    public boolean isSkipFailedMessage() {
        return getConfiguration().isSkipFailedMessage();
    }

    /**
     * If the mail consumer cannot retrieve a given mail message, then this option allows to skip
     * the message and move on to retrieve the next mail message.
     * <p/>
     * The default behavior would be the consumer throws an exception and no mails from the batch would be able to be routed by Camel.
     * @param skipFailedMessage
     */
    public void setSkipFailedMessage(boolean skipFailedMessage) {
        getConfiguration().setSkipFailedMessage(skipFailedMessage);
    }

    public boolean isHandleFailedMessage() {
        return getConfiguration().isHandleFailedMessage();
    }

    /**
     * If the mail consumer cannot retrieve a given mail message, then this option allows to handle
     * the caused exception by the consumer's error handler. By enable the bridge error handler on the consumer,
     * then the Camel routing error handler can handle the exception instead.
     * <p/>
     * The default behavior would be the consumer throws an exception and no mails from the batch would be able to be routed by Camel.
     * @param handleFailedMessage
     */
    public void setHandleFailedMessage(boolean handleFailedMessage) {
        getConfiguration().setHandleFailedMessage(handleFailedMessage);
    }

    public AttachmentsContentTransferEncodingResolver getAttachmentsContentTransferEncodingResolver() {
        return getConfiguration().getAttachmentsContentTransferEncodingResolver();
    }

    /**
     * To use a custom AttachmentsContentTransferEncodingResolver to resolve what content-type-encoding to use for attachments.
     * @param attachmentsContentTransferEncodingResolver
     */
    public void setAttachmentsContentTransferEncodingResolver(AttachmentsContentTransferEncodingResolver attachmentsContentTransferEncodingResolver) {
        getConfiguration().setAttachmentsContentTransferEncodingResolver(attachmentsContentTransferEncodingResolver);
    }
}
