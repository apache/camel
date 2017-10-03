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
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.mail.util.ByteArrayDataSource;

import org.apache.camel.Attachment;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.DefaultAttachment;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and {@link Message} to and
 * from a Mail {@link MimeMessage}
 */
public class MailBinding {

    private static final Logger LOG = LoggerFactory.getLogger(MailBinding.class);
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

        // camel message headers takes precedence over endpoint configuration
        if (hasRecipientHeaders(exchange)) {
            setRecipientFromCamelMessage(mimeMessage, endpoint.getConfiguration(), exchange);
        } else {
            // fallback to endpoint configuration
            setRecipientFromEndpointConfiguration(mimeMessage, endpoint, exchange);
        }

        // set the replyTo if it was passed in as an option in the uri. Note: if it is in both the URI
        // and headers the headers win.
        String replyTo = exchange.getIn().getHeader("Reply-To", String.class);
        if (replyTo == null) {
            replyTo = endpoint.getConfiguration().getReplyTo();
        }
        if (replyTo != null) {
            List<InternetAddress> replyToAddresses = new ArrayList<InternetAddress>();
            for (String reply : splitRecipients(replyTo)) {
                replyToAddresses.add(asEncodedInternetAddress(reply.trim(), determineCharSet(endpoint.getConfiguration(), exchange)));
            }
            mimeMessage.setReplyTo(replyToAddresses.toArray(new InternetAddress[replyToAddresses.size()]));
        }

        // must have at least one recipients otherwise we do not know where to send the mail
        if (mimeMessage.getAllRecipients() == null) {
            throw new IllegalArgumentException("The mail message does not have any recipients set.");
        }

        // set the subject if it was passed in as an option in the uri. Note: if it is in both the URI
        // and headers the headers win.
        String subject = endpoint.getConfiguration().getSubject();
        if (subject != null) {
            mimeMessage.setSubject(subject, IOHelper.getCharsetName(exchange, false));
        }

        // append the rest of the headers (no recipients) that could be subject, reply-to etc.
        appendHeadersFromCamelMessage(mimeMessage, endpoint.getConfiguration(), exchange);

        if (empty(mimeMessage.getFrom())) {
            // lets default the address to the endpoint destination
            String from = endpoint.getConfiguration().getFrom();
            mimeMessage.setFrom(asEncodedInternetAddress(from, determineCharSet(endpoint.getConfiguration(), exchange)));
        }

        // if there is an alternative body provided, set up a mime multipart alternative message
        if (hasAlternativeBody(endpoint.getConfiguration(), exchange)) {
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

        // fix content-type to have space after semi colons, otherwise some mail servers will choke
        if (contentType != null && contentType.contains(";")) {
            contentType = MailUtils.padContentType(contentType);
        }

        if (contentType != null) {
            // no charset in content-type, then try to see if we can determine one
            String charset = determineCharSet(configuration, exchange);
            // must replace charset, even with null in case its an unsupported charset
            contentType = MailUtils.replaceCharSet(contentType, charset);
        }

        LOG.trace("Determined Content-Type: {}", contentType);

        return contentType;
    }

    protected static String determineCharSet(MailConfiguration configuration, Exchange exchange) {

        // see if we got any content type set
        String contentType = configuration.getContentType();
        if (exchange.getIn().getHeader("contentType") != null) {
            contentType = exchange.getIn().getHeader("contentType", String.class);
        } else if (exchange.getIn().getHeader(Exchange.CONTENT_TYPE) != null) {
            contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        }

        // look for charset
        String charset = MailUtils.getCharSetFromContentType(contentType);
        if (charset != null) {
            charset = IOHelper.normalizeCharset(charset);
            if (charset != null) {
                boolean supported;
                try {
                    supported = Charset.isSupported(charset);
                } catch (IllegalCharsetNameException e) {
                    supported = false;
                }
                if (supported) {
                    return charset;
                } else if (!configuration.isIgnoreUnsupportedCharset()) {
                    return charset;
                } else if (configuration.isIgnoreUnsupportedCharset()) {
                    LOG.warn("Charset: " + charset + " is not supported and cannot be used as charset in Content-Type header.");
                    return null;
                }
            }
        }

        // Using the charset header of exchange as a fall back
        return IOHelper.getCharsetName(exchange, false);
    }

    protected String populateContentOnMimeMessage(MimeMessage part, MailConfiguration configuration, Exchange exchange)
        throws MessagingException, IOException {

        String contentType = determineContentType(configuration, exchange);

        LOG.trace("Using Content-Type {} for MimeMessage: {}", contentType, part);

        String body = exchange.getIn().getBody(String.class);
        if (body == null) {
            body = "";
        }

        // always store content in a byte array data store to avoid various content type and charset issues
        DataSource ds = new ByteArrayDataSource(body, contentType);
        part.setDataHandler(new DataHandler(ds));

        // set the content type header afterwards
        part.setHeader("Content-Type", contentType);

        return contentType;
    }

    protected String populateContentOnBodyPart(BodyPart part, MailConfiguration configuration, Exchange exchange)
        throws MessagingException, IOException {

        String contentType = determineContentType(configuration, exchange);

        if (contentType != null) {
            LOG.trace("Using Content-Type {} for BodyPart: {}", contentType, part);

            // always store content in a byte array data store to avoid various content type and charset issues
            String data = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange.getIn().getBody());
            // use empty data if the body was null for some reason (otherwise there is a NPE)
            data = data != null ? data : "";

            DataSource ds = new ByteArrayDataSource(data, contentType);
            part.setDataHandler(new DataHandler(ds));

            // set the content type header afterwards
            part.setHeader("Content-Type", contentType);
        }

        return contentType;
    }

    /**
     * Extracts the body from the Mail message
     */
    public Object extractBodyFromMail(Exchange exchange, MailMessage mailMessage) {
        Message message = mailMessage.getMessage();
        try {
            if (((MailEndpoint) exchange.getFromEndpoint()).getConfiguration().isMapMailMessage()) {
                return message.getContent();
            }
            return message; // raw message
        } catch (Exception e) {
            // try to fix message in case it has an unsupported encoding in the Content-Type header
            UnsupportedEncodingException uee = ObjectHelper.getException(UnsupportedEncodingException.class, e);
            if (uee != null) {
                LOG.debug("Unsupported encoding detected: " + uee.getMessage());
                try {
                    String contentType = message.getContentType();
                    String type = ObjectHelper.before(contentType, "charset=");
                    if (type != null) {
                        // try again with fixed content type
                        LOG.debug("Trying to extract mail message again with fixed Content-Type: " + type);
                        // Since message is read-only, we need to use a copy
                        MimeMessage messageCopy = new MimeMessage((MimeMessage) message);
                        messageCopy.setHeader("Content-Type", type);
                        Object body = messageCopy.getContent();
                        // If we got this far, our fix worked...
                        // Replace the MailMessage's Message with the copy
                        mailMessage.setMessage(messageCopy);
                        return body;
                    }
                } catch (Exception e2) {
                    // fall through and let original exception be thrown
                }
            }

            throw new RuntimeCamelException("Failed to extract body due to: " + e.getMessage()
                    + ". Exchange: " + exchange + ". Message: " + message, e);
        }
    }

    /**
     * Parses the attachments of the given mail message and adds them to the map
     *
     * @param message the mail message with attachments
     * @param map     the map to add found attachments (attachmentFilename is the key)
     */
    public void extractAttachmentsFromMail(Message message, Map<String, Attachment> map)
        throws MessagingException, IOException {

        LOG.trace("Extracting attachments +++ start +++");

        Object content = message.getContent();
        if (content instanceof Multipart) {
            extractAttachmentsFromMultipart((Multipart) content, map);
        } else if (content != null) {
            LOG.trace("No attachments to extract as content is not Multipart: " + content.getClass().getName());
        }

        LOG.trace("Extracting attachments +++ done +++");
    }

    protected void extractAttachmentsFromMultipart(Multipart mp, Map<String, Attachment> map)
        throws MessagingException, IOException {

        for (int i = 0; i < mp.getCount(); i++) {
            Part part = mp.getBodyPart(i);
            LOG.trace("Part #" + i + ": " + part);

            if (part.isMimeType("multipart/*")) {
                LOG.trace("Part #" + i + ": is mimetype: multipart/*");
                extractAttachmentsFromMultipart((Multipart) part.getContent(), map);
            } else {
                String disposition = part.getDisposition();
                String fileName = part.getFileName();

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Part #{}: Disposition: {}", i, disposition);
                    LOG.trace("Part #{}: Description: {}", i, part.getDescription());
                    LOG.trace("Part #{}: ContentType: {}", i, part.getContentType());
                    LOG.trace("Part #{}: FileName: {}", i, fileName);
                    LOG.trace("Part #{}: Size: {}", i, part.getSize());
                    LOG.trace("Part #{}: LineCount: {}", i, part.getLineCount());
                }

                if (validDisposition(disposition, fileName)
                        || fileName != null) {
                    LOG.debug("Mail contains file attachment: {}", fileName);
                    if (!map.containsKey(fileName)) {
                        // Parts marked with a disposition of Part.ATTACHMENT are clearly attachments
                        DefaultAttachment camelAttachment = new DefaultAttachment(part.getDataHandler());
                        @SuppressWarnings("unchecked")
                        Enumeration<Header> headers = part.getAllHeaders();
                        while (headers.hasMoreElements()) {
                            Header header = headers.nextElement();
                            camelAttachment.addHeader(header.getName(), header.getValue());
                        }
                        map.put(fileName, camelAttachment);
                    } else {
                        LOG.warn("Cannot extract duplicate file attachment: {}.", fileName);
                    }
                }
            }
        }
    }

    private boolean validDisposition(String disposition, String fileName) {
        return disposition != null
                && fileName != null
                && (disposition.equalsIgnoreCase(Part.ATTACHMENT) || disposition.equalsIgnoreCase(Part.INLINE));
    }

    /**
     * Appends the Mail headers from the Camel {@link MailMessage}
     */
    protected void appendHeadersFromCamelMessage(MimeMessage mimeMessage, MailConfiguration configuration, Exchange exchange)
        throws MessagingException, IOException {

        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            if (headerValue != null) {
                if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange)) {
                    if (headerName.equalsIgnoreCase("subject")) {
                        mimeMessage.setSubject(asString(exchange, headerValue), determineCharSet(configuration, exchange));
                        continue;
                    }
                    if (headerName.equalsIgnoreCase("from")) {
                        mimeMessage.setFrom(asEncodedInternetAddress(asString(exchange, headerValue), determineCharSet(configuration, exchange)));
                        continue;
                    }
                    if (headerName.equalsIgnoreCase("sender")) {
                        mimeMessage.setSender(asEncodedInternetAddress(asString(exchange, headerValue), determineCharSet(configuration, exchange)));
                        continue;
                    }
                    if (isRecipientHeader(headerName)) {
                        // skip any recipients as they are handled specially
                        continue;
                    }

                    // alternative body should also be skipped
                    if (headerName.equalsIgnoreCase(configuration.getAlternativeBodyHeader())) {
                        // skip alternative body
                        continue;
                    }

                    // Mail messages can repeat the same header...
                    if (ObjectConverter.isCollection(headerValue)) {
                        Iterator<?> iter = ObjectHelper.createIterator(headerValue);
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

    private void setRecipientFromCamelMessage(MimeMessage mimeMessage, MailConfiguration configuration, Exchange exchange) throws MessagingException, IOException {
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            if (headerValue != null && isRecipientHeader(headerName)) {
                // special handling of recipients
                if (ObjectConverter.isCollection(headerValue)) {
                    Iterator<?> iter = ObjectHelper.createIterator(headerValue);
                    while (iter.hasNext()) {
                        Object recipient = iter.next();
                        appendRecipientToMimeMessage(mimeMessage, configuration, exchange, headerName, asString(exchange, recipient));
                    }
                } else {
                    appendRecipientToMimeMessage(mimeMessage, configuration, exchange, headerName, asString(exchange, headerValue));
                }
            }
        }
    }

    /**
     * Appends the Mail headers from the endpoint configuration.
     */
    protected void setRecipientFromEndpointConfiguration(MimeMessage mimeMessage, MailEndpoint endpoint, Exchange exchange)
        throws MessagingException, IOException {

        Map<Message.RecipientType, String> recipients = endpoint.getConfiguration().getRecipients();
        if (recipients.containsKey(Message.RecipientType.TO)) {
            appendRecipientToMimeMessage(mimeMessage, endpoint.getConfiguration(), exchange, Message.RecipientType.TO.toString(), recipients.get(Message.RecipientType.TO));
        }
        if (recipients.containsKey(Message.RecipientType.CC)) {
            appendRecipientToMimeMessage(mimeMessage, endpoint.getConfiguration(), exchange, Message.RecipientType.CC.toString(), recipients.get(Message.RecipientType.CC));
        }
        if (recipients.containsKey(Message.RecipientType.BCC)) {
            appendRecipientToMimeMessage(mimeMessage, endpoint.getConfiguration(), exchange, Message.RecipientType.BCC.toString(), recipients.get(Message.RecipientType.BCC));
        }
    }

    /**
     * Appends the Mail attachments from the Camel {@link MailMessage}
     */
    protected void appendAttachmentsFromCamel(MimeMessage mimeMessage, MailConfiguration configuration, Exchange exchange)
        throws MessagingException, IOException {

        // Put parts in message
        mimeMessage.setContent(createMixedMultipartAttachments(configuration, exchange));
    }

    private MimeMultipart createMixedMultipartAttachments(MailConfiguration configuration, Exchange exchange)
        throws MessagingException, IOException {

        // fill the body with text
        MimeMultipart multipart = new MimeMultipart();
        multipart.setSubType("mixed");
        addBodyToMultipart(configuration, multipart, exchange);
        String partDisposition = configuration.isUseInlineAttachments() ? Part.INLINE : Part.ATTACHMENT;
        AttachmentsContentTransferEncodingResolver contentTransferEncodingResolver = configuration.getAttachmentsContentTransferEncodingResolver();
        if (exchange.getIn().hasAttachments()) {
            addAttachmentsToMultipart(multipart, partDisposition, contentTransferEncodingResolver, exchange);
        }
        return multipart;
    }

    protected void addAttachmentsToMultipart(MimeMultipart multipart, String partDisposition,
                                             AttachmentsContentTransferEncodingResolver encodingResolver, Exchange exchange) throws MessagingException {
        LOG.trace("Adding attachments +++ start +++");
        int i = 0;
        for (Map.Entry<String, Attachment> entry : exchange.getIn().getAttachmentObjects().entrySet()) {
            String attachmentFilename = entry.getKey();
            Attachment attachment = entry.getValue();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Attachment #{}: Disposition: {}", i, partDisposition);
                LOG.trace("Attachment #{}: DataHandler: {}", i, attachment.getDataHandler());
                LOG.trace("Attachment #{}: FileName: {}", i, attachmentFilename);
            }
            if (attachment != null) {
                if (shouldAddAttachment(exchange, attachmentFilename, attachment.getDataHandler())) {
                    // Create another body part
                    BodyPart messageBodyPart = new MimeBodyPart();
                    // Set the data handler to the attachment
                    messageBodyPart.setDataHandler(attachment.getDataHandler());

                    // Set headers to the attachment
                    for (String headerName : attachment.getHeaderNames()) {
                        List<String> values = attachment.getHeaderAsList(headerName);
                        for (String value : values) {
                            messageBodyPart.setHeader(headerName, value);
                        }
                    }

                    if (attachmentFilename.toLowerCase().startsWith("cid:")) {
                        // add a Content-ID header to the attachment
                        // must use angle brackets according to RFC: http://www.ietf.org/rfc/rfc2392.txt
                        messageBodyPart.addHeader("Content-ID", "<" + attachmentFilename.substring(4) + ">");
                        // Set the filename without the cid
                        messageBodyPart.setFileName(attachmentFilename.substring(4));
                    } else {
                        // Set the filename
                        messageBodyPart.setFileName(attachmentFilename);
                    }

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

                    // set Content-Transfer-Encoding using resolver if possible
                    resolveContentTransferEncoding(encodingResolver, i, messageBodyPart);
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

    protected void resolveContentTransferEncoding(AttachmentsContentTransferEncodingResolver resolver, int i, BodyPart messageBodyPart) throws MessagingException {
        if (resolver != null) {
            String contentTransferEncoding = resolver.resolveContentTransferEncoding(messageBodyPart);
            LOG.trace("Attachment #{}: Using content transfer encoding resolver: {} resolved content transfer encoding as: {}", i, resolver, contentTransferEncoding);
            if (contentTransferEncoding != null) {
                messageBodyPart.setHeader("Content-Transfer-Encoding", contentTransferEncoding);
            }
        }
    }

    protected void createMultipartAlternativeMessage(MimeMessage mimeMessage, MailConfiguration configuration, Exchange exchange)
        throws MessagingException, IOException {

        MimeMultipart multipartAlternative = new MimeMultipart("alternative");
        mimeMessage.setContent(multipartAlternative);

        MimeBodyPart plainText = new MimeBodyPart();
        plainText.setText(getAlternativeBody(configuration, exchange), determineCharSet(configuration, exchange));
        // remove the header with the alternative mail now that we got it
        // otherwise it might end up twice in the mail reader
        exchange.getIn().removeHeader(configuration.getAlternativeBodyHeader());
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
                AttachmentsContentTransferEncodingResolver resolver = configuration.getAttachmentsContentTransferEncodingResolver();
                addAttachmentsToMultipart(multipartRelated, Part.INLINE, resolver, exchange);
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

    protected Map<String, Object> extractHeadersFromMail(Message mailMessage, Exchange exchange) throws MessagingException, IOException {
        Map<String, Object> answer = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<?> names = mailMessage.getAllHeaders();

        MailConfiguration mailConfiguration = ((MailEndpoint) exchange.getFromEndpoint()).getConfiguration();
        while (names.hasMoreElements()) {
            Header header = (Header) names.nextElement();

            String value = header.getValue();
            if (value != null && mailConfiguration.isMimeDecodeHeaders()) {
                value = MimeUtility.decodeText(MimeUtility.unfold(value));
            }

            if (headerFilterStrategy != null && !headerFilterStrategy.applyFilterToExternalHeaders(header.getName(), value, exchange)) {
                CollectionHelper.appendValue(answer, header.getName(), value);
            }
        }
        // if the message is a multipart message, do not set the content type to multipart/*
        if (mailConfiguration.isMapMailMessage()) {
            Object content = mailMessage.getContent();
            if (content instanceof MimeMultipart) {
                MimeMultipart multipart = (MimeMultipart)content;
                int size = multipart.getCount();
                for (int i = 0; i < size; i++) {
                    BodyPart part = multipart.getBodyPart(i);
                    content = part.getContent();
                    // in case of nested multiparts iterate into them
                    while (content instanceof MimeMultipart) {
                        if (multipart.getCount() < 1) {
                            break;
                        }
                        part = ((MimeMultipart)content).getBodyPart(0);
                        content = part.getContent();
                    }
                    if (part.getContentType().toLowerCase().startsWith("text")) {
                        answer.put(Exchange.CONTENT_TYPE, part.getContentType());
                        break;
                    }
                }
            }
        }

        return answer;
    }

    private static void appendRecipientToMimeMessage(MimeMessage mimeMessage, MailConfiguration configuration, Exchange exchange,
                                                     String type, String recipient) throws MessagingException, IOException {
        List<InternetAddress> recipientsAddresses = new ArrayList<InternetAddress>();
        for (String line : splitRecipients(recipient)) {
            String address = line.trim();
            // Only add the address which is not empty
            if (ObjectHelper.isNotEmpty(address)) {
                recipientsAddresses.add(asEncodedInternetAddress(address, determineCharSet(configuration, exchange)));
            }
        }

        mimeMessage.addRecipients(asRecipientType(type), recipientsAddresses.toArray(new InternetAddress[recipientsAddresses.size()]));
    }

    private static String[] splitRecipients(String recipients) {
        // we support that multi recipient can be given as a string separated by comma or semicolon
        // regex ignores comma and semicolon inside of double quotes
        return recipients.split("[,;]++(?=(?:(?:[^\\\"]*+\\\"){2})*+[^\\\"]*+$)");
    }

    /**
     * Does the given camel message contain any To, CC or BCC header names?
     */
    private static boolean hasRecipientHeaders(Exchange exchange) {
        for (String key : exchange.getIn().getHeaders().keySet()) {
            if (isRecipientHeader(key)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean hasAlternativeBody(MailConfiguration configuration, Exchange exchange) {
        return getAlternativeBody(configuration, exchange) != null;
    }

    protected static String getAlternativeBody(MailConfiguration configuration, Exchange exchange) {
        String alternativeBodyHeader = configuration.getAlternativeBodyHeader();
        return exchange.getIn().getHeader(alternativeBodyHeader, java.lang.String.class);
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

    /**
     * Returns internet address with encoded personal.
     */
    private static InternetAddress asEncodedInternetAddress(String address, String charset)
        throws UnsupportedEncodingException, AddressException {

        InternetAddress internetAddress = new InternetAddress(address);
        internetAddress.setPersonal(internetAddress.getPersonal(), charset);
        return internetAddress;
    }

}
