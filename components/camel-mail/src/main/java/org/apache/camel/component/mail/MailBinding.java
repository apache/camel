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

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and {@link Message} to and
 * from a Mail {@link MimeMessage}
 *
 * @version $Revision$
 */
public class MailBinding {

    private static final transient Log LOG = LogFactory.getLog(MailBinding.class);
    private HeaderFilterStrategy headerFilterStrategy;
    private ContentTypeResolver contentTypeResolver;

    public MailBinding() {
        headerFilterStrategy = new DefaultHeaderFilterStrategy();
    }

    public MailBinding(HeaderFilterStrategy headerFilterStrategy, ContentTypeResolver contentTypeResolver) {
        this.headerFilterStrategy = headerFilterStrategy;
        this.contentTypeResolver = contentTypeResolver;
    }

    public void populateMailMessage(MailEndpoint endpoint, MimeMessage mimeMessage, Exchange exchange)
        throws MessagingException, IOException {

        // camel message headers takes presedence over endpoint configuration
        if (hasRecipientHeaders(exchange.getIn())) {
            setRecipientFromCamelMessage(mimeMessage, exchange, exchange.getIn());
        } else {
            // fallback to endpoint configuration
            setRecipientFromEndpointConfiguration(mimeMessage, endpoint);
        }

        // must have at least one recipients otherwise we do not know where to send the mail
        if (mimeMessage.getAllRecipients() == null) {
            throw new IllegalArgumentException("The mail message does not have any recipients set.");
        }

        // append the rest of the headers (no recipients) that could be subject, reply-to etc.
        appendHeadersFromCamelMessage(mimeMessage, exchange, exchange.getIn());

        if (empty(mimeMessage.getFrom())) {
            // lets default the address to the endpoint destination
            String from = endpoint.getConfiguration().getFrom();
            mimeMessage.setFrom(new InternetAddress(from));
        }

        // if there is an alternativebody provided, set up a mime multipart alternative message
        if (hasAlternativeBody(endpoint.getConfiguration(), exchange.getIn())) {
            createMultipartAlternativeMessage(mimeMessage, endpoint.getConfiguration(), exchange);
        } else {
            if (exchange.getIn().hasAttachments()) {
                appendAttachmentsFromCamel(mimeMessage, endpoint.getConfiguration(), exchange);
            } else {
                populateContentOnMimeMessage(mimeMessage, endpoint.getConfiguration(), exchange);
            }
        }
    }

    protected String determineContentType(MailConfiguration configuration, Exchange exchange) {
        // see if we got any content type set
        String contentType = configuration.getContentType();
        if (exchange.getIn().getHeader("contentType") != null) {
            contentType = exchange.getIn().getHeader("contentType", String.class);
        } else if (exchange.getIn().getHeader(Exchange.CONTENT_TYPE) != null) {
            contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        }

        // fix content type to include a space after semi colon if missing
        if (contentType != null && contentType.contains(";")) {
            String before = ObjectHelper.before(contentType, ";");
            String after = ObjectHelper.after(contentType, ";");
            if (before != null && after != null) {
                contentType = before.trim() + "; " + after.trim();
            }
        }

        return contentType;
    }

    protected String populateContentOnMimeMessage(MimeMessage part, MailConfiguration configuration, Exchange exchange) throws MessagingException, IOException {
        String contentType = determineContentType(configuration, exchange);
        if (contentType == null) {
            part.setText(exchange.getIn().getBody(String.class));
        } else {
            if (contentType.startsWith("text/plain")) {
                String charset = ObjectHelper.after(contentType, "charset=");
                if (charset != null) {
                    part.setText(exchange.getIn().getBody(String.class), charset.trim());
                } else {
                    part.setText(exchange.getIn().getBody(String.class));
                }
            } else {
                // store content in a byte array data store
                DataSource ds = new ByteArrayDataSource(exchange.getIn().getBody(String.class), contentType);
                part.setDataHandler(new DataHandler(ds));
            }
            part.setHeader("Content-Type", contentType);
        }
        return contentType;
    }

    protected String populateContentOnBodyPart(BodyPart part, MailConfiguration configuration, Exchange exchange) throws MessagingException, IOException {
        String contentType = determineContentType(configuration, exchange);
        if (contentType == null) {
            part.setText(exchange.getIn().getBody(String.class));
        } else {
            if (contentType.startsWith("text/plain")) {
                part.setText(exchange.getIn().getBody(String.class));
            } else {
                // store content in a byte array data store
                DataSource ds = new ByteArrayDataSource(exchange.getIn().getBody(String.class), contentType);
                part.setDataHandler(new DataHandler(ds));
            }
            part.setHeader("Content-Type", contentType);
        }
        return contentType;
    }

    /**
     * Extracts the body from the Mail message
     */
    public Object extractBodyFromMail(MailExchange exchange, Message message) {
        try {
            return message.getContent();
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to extract body due to: " + e.getMessage()
                + ". Exchange: " + exchange + ". Message: " + message, e);
        }
    }

    /**
     * Appends the Mail headers from the Camel {@link MailMessage}
     */
    protected void appendHeadersFromCamelMessage(MimeMessage mimeMessage, Exchange exchange,
                                                 org.apache.camel.Message camelMessage)
        throws MessagingException {

        for (Map.Entry<String, Object> entry : camelMessage.getHeaders().entrySet()) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            if (headerValue != null) {
                if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange)) {

                    if (isRecipientHeader(headerName)) {
                        // skip any recipients as they are handled specially
                        continue;
                    }

                    // Mail messages can repeat the same header...
                    if (ObjectConverter.isCollection(headerValue)) {
                        Iterator iter = ObjectHelper.createIterator(headerValue);
                        while (iter.hasNext()) {
                            Object value = iter.next();
                            mimeMessage.addHeader(headerName, asString(exchange, value));
                        }
                    } else {
                        mimeMessage.setHeader(headerName, asString(exchange, headerValue));
                    }
                }
            }
        }
    }

    private void setRecipientFromCamelMessage(MimeMessage mimeMessage, Exchange exchange,
                                              org.apache.camel.Message camelMessage)
        throws MessagingException {

        for (Map.Entry<String, Object> entry : camelMessage.getHeaders().entrySet()) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            if (headerValue != null && isRecipientHeader(headerName)) {
                // special handling of recipients
                if (ObjectConverter.isCollection(headerValue)) {
                    Iterator iter = ObjectHelper.createIterator(headerValue);
                    while (iter.hasNext()) {
                        Object recipient = iter.next();
                        appendRecipientToMimeMessage(mimeMessage, headerName, asString(exchange, recipient));
                    }
                } else {
                    appendRecipientToMimeMessage(mimeMessage, headerName, asString(exchange, headerValue));
                }
            }
        }
    }

    /**
     * Appends the Mail headers from the endpoint configuraiton.
     */
    protected void setRecipientFromEndpointConfiguration(MimeMessage mimeMessage, MailEndpoint endpoint)
        throws MessagingException {

        Map<Message.RecipientType, String> recipients = endpoint.getConfiguration().getRecipients();
        if (recipients.containsKey(Message.RecipientType.TO)) {
            appendRecipientToMimeMessage(mimeMessage, Message.RecipientType.TO.toString(), recipients.get(Message.RecipientType.TO));
        }
        if (recipients.containsKey(Message.RecipientType.CC)) {
            appendRecipientToMimeMessage(mimeMessage, Message.RecipientType.CC.toString(), recipients.get(Message.RecipientType.CC));
        }
        if (recipients.containsKey(Message.RecipientType.BCC)) {
            appendRecipientToMimeMessage(mimeMessage, Message.RecipientType.BCC.toString(), recipients.get(Message.RecipientType.BCC));
        }
    }

    /**
     * Appends the Mail attachments from the Camel {@link MailMessage}
     */
    protected void appendAttachmentsFromCamel(MimeMessage mimeMessage, MailConfiguration configuration,
                                              Exchange exchange) throws MessagingException, IOException {
        // Put parts in message
        mimeMessage.setContent(createMixedMultipartAttachments(configuration, exchange));
    }

    private MimeMultipart createMixedMultipartAttachments(MailConfiguration configuration, Exchange exchange)
            throws MessagingException, IOException {
        // fill the body with text
        MimeMultipart multipart = new MimeMultipart();
        multipart.setSubType("mixed");
        addBodyToMultipart(configuration, multipart, exchange);
        String partDisposition = configuration.isUseInlineAttachments() ?  Part.INLINE : Part.ATTACHMENT;
        if (exchange.getIn().hasAttachments()) {
            addAttachmentsToMultipart(multipart, partDisposition, exchange);
        }
        return multipart;
    }

    protected void addAttachmentsToMultipart(MimeMultipart multipart, String partDisposition, Exchange exchange) throws MessagingException {
        LOG.trace("Adding attachments +++ start +++");
        int i = 0;
        for (Map.Entry<String, DataHandler> entry : exchange.getIn().getAttachments().entrySet()) {
            String attachmentFilename = entry.getKey();
            DataHandler handler = entry.getValue();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Attachment #" + i + ": Disposition: " + partDisposition);
                LOG.trace("Attachment #" + i + ": DataHandler: " + handler);
                LOG.trace("Attachment #" + i + ": FileName: " + attachmentFilename);
            }
            if (handler != null) {
                if (shouldAddAttachment(exchange, attachmentFilename, handler)) {
                    // Create another body part
                    BodyPart messageBodyPart = new MimeBodyPart();
                    // Set the data handler to the attachment
                    messageBodyPart.setDataHandler(handler);
                    
                    if (attachmentFilename.toLowerCase().startsWith("cid:")) {
                        // add a Content-ID header to the attachment
                        messageBodyPart.addHeader("Content-ID", attachmentFilename.substring(4));
                    }

                    // Set the filename
                    messageBodyPart.setFileName(attachmentFilename);
                    LOG.trace("Attachment #" + i + ": ContentType: " + messageBodyPart.getContentType());

                    if (contentTypeResolver != null) {
                        String contentType = contentTypeResolver.resolveContentType(attachmentFilename);
                        LOG.trace("Attachment #" + i + ": Using content type resolver: " + contentTypeResolver + " resolved content type as: " + contentType);
                        if (contentType != null) {
                            String value = contentType + "; name=" + attachmentFilename;
                            messageBodyPart.setHeader("Content-Type", value);
                            LOG.trace("Attachment #" + i + ": ContentType: " + messageBodyPart.getContentType());
                        }
                    }

                    // Set Disposition
                    messageBodyPart.setDisposition(partDisposition);
                    // Add part to multipart
                    multipart.addBodyPart(messageBodyPart);
                } else {
                    LOG.trace("shouldAddAttachment: false");
                }
            } else {
                LOG.warn("Cannot add attachment: " + attachmentFilename + " as DataHandler is null");
            }
            i++;
        }
        LOG.trace("Adding attachments +++ done +++");
    }

    protected void createMultipartAlternativeMessage(MimeMessage mimeMessage, MailConfiguration configuration,
                                                     Exchange exchange) throws MessagingException, IOException {

        MimeMultipart multipartAlternative = new MimeMultipart("alternative");
        mimeMessage.setContent(multipartAlternative);

        BodyPart plainText = new MimeBodyPart();
        plainText.setText(getAlternativeBody(configuration, exchange.getIn()));
        multipartAlternative.addBodyPart(plainText);

        // if there are no attachments, add the body to the same mulitpart message
        if (!exchange.getIn().hasAttachments()) {
            addBodyToMultipart(configuration, multipartAlternative, exchange);
        } else {
            // if there are attachments, but they aren't set to be inline, add them to
            // treat them as normal. It will append a multipart-mixed with the attachments and the body text
            if (!configuration.isUseInlineAttachments()) {
                BodyPart mixedAttachments = new MimeBodyPart();
                mixedAttachments.setContent(createMixedMultipartAttachments(configuration, exchange));
                multipartAlternative.addBodyPart(mixedAttachments);
            } else {
                // if the attachments are set to be inline, attach them as inline attachments
                MimeMultipart multipartRelated = new MimeMultipart("related");
                BodyPart related = new MimeBodyPart();

                related.setContent(multipartRelated);
                multipartAlternative.addBodyPart(related);

                addBodyToMultipart(configuration, multipartRelated, exchange);

                addAttachmentsToMultipart(multipartRelated, Part.INLINE, exchange);
            }
        }

    }

    protected void addBodyToMultipart(MailConfiguration configuration, MimeMultipart activeMultipart, Exchange exchange)
            throws MessagingException, IOException {

        BodyPart bodyMessage = new MimeBodyPart();
        populateContentOnBodyPart(bodyMessage, configuration, exchange);
        activeMultipart.addBodyPart(bodyMessage);
    }

    /**
     * Strategy to allow filtering of attachments which are added on the Mail message
     */
    protected boolean shouldAddAttachment(Exchange exchange, String attachmentFilename, DataHandler handler) {
        return true;
    }

    protected Map<String, Object> extractHeadersFromMail(Message mailMessage, Exchange exchange) throws MessagingException {
        Map<String, Object> answer = new HashMap<String, Object>();
        Enumeration names = mailMessage.getAllHeaders();

        while (names.hasMoreElements()) {
            Header header = (Header)names.nextElement();
            String[] value = mailMessage.getHeader(header.getName());
            if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(header.getName(), value, exchange)) {
                // toLowerCase() for doing case insensitive search
                if (value.length == 1) {
                    CollectionHelper.appendValue(answer, header.getName().toLowerCase(), value[0]);
                } else {
                    CollectionHelper.appendValue(answer, header.getName().toLowerCase(), value);
                }
            }
        }

        return answer;
    }

    private static void appendRecipientToMimeMessage(MimeMessage mimeMessage, String type, String recipient)
        throws MessagingException {

        // we support that multi recipient can be given as a string separated by comma or semicolon
        String[] lines = recipient.split("[,|;]");
        for (String line : lines) {
            line = line.trim();
            mimeMessage.addRecipients(asRecipientType(type), line);
        }
    }

    /**
     * Does the given camel message contain any To, CC or BCC header names?
     */
    private static boolean hasRecipientHeaders(org.apache.camel.Message camelMessage) {
        for (String key : camelMessage.getHeaders().keySet()) {
            if (isRecipientHeader(key)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean hasAlternativeBody(MailConfiguration configuration, org.apache.camel.Message camelMessage) {
        return getAlternativeBody(configuration, camelMessage) != null;
    }

    protected static String getAlternativeBody(MailConfiguration configuration, org.apache.camel.Message camelMessage) {
        String alternativeBodyHeader = configuration.getAlternativeBodyHeader();
        return camelMessage.getHeader(alternativeBodyHeader, java.lang.String.class);
    }

    /**
     * Is the given key a mime message recipient header (To, CC or BCC)
     */
    private static boolean isRecipientHeader(String key) {
        if (Message.RecipientType.TO.toString().equalsIgnoreCase(key)) {
            return true;
        } else if (Message.RecipientType.CC.toString().equalsIgnoreCase(key)) {
            return true;
        } else if (Message.RecipientType.BCC.toString().equalsIgnoreCase(key)) {
            return true;
        }
        return false;
    }

    /**
     * Returns the RecipientType object.
     */
    private static Message.RecipientType asRecipientType(String type) {
        if (Message.RecipientType.TO.toString().equalsIgnoreCase(type)) {
            return Message.RecipientType.TO;
        } else if (Message.RecipientType.CC.toString().equalsIgnoreCase(type)) {
            return Message.RecipientType.CC;
        } else if (Message.RecipientType.BCC.toString().equalsIgnoreCase(type)) {
            return Message.RecipientType.BCC;
        }
        throw new IllegalArgumentException("Unknown recipient type: " + type);
    }


    private static boolean empty(Address[] addresses) {
        return addresses == null || addresses.length == 0;
    }

    private static String asString(Exchange exchange, Object value) {
        return exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
    }

}
