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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.util.ByteArrayDataSource;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mail.MailConstants.MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_NEVER;
import static org.apache.camel.component.mail.MailConstants.MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_UUID;
import static org.apache.camel.component.mail.MailConstants.MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_NEVER;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and {@link Message} to and from a Mail
 * {@link MimeMessage}
 */
public class MailBinding {

    private static final Logger LOG = LoggerFactory.getLogger(MailBinding.class);
    private final HeaderFilterStrategy headerFilterStrategy;
    private ContentTypeResolver contentTypeResolver;
    private boolean decodeFilename;
    private boolean mapMailMessage = true;
    private boolean failOnDuplicateAttachment;
    private String generateMissingAttachmentNames;
    private String handleDuplicateAttachmentNames;

    public MailBinding() {
        headerFilterStrategy = new DefaultHeaderFilterStrategy();
    }

    @Deprecated
    public MailBinding(HeaderFilterStrategy headerFilterStrategy, ContentTypeResolver contentTypeResolver,
                       boolean decodeFilename) {
        this(headerFilterStrategy, contentTypeResolver, decodeFilename, true, false,
             MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_NEVER, MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_NEVER);
    }

    public MailBinding(HeaderFilterStrategy headerFilterStrategy, ContentTypeResolver contentTypeResolver,
                       boolean decodeFilename, boolean mapMailMessage) {
        this(headerFilterStrategy, contentTypeResolver, decodeFilename, mapMailMessage, false,
             MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_NEVER, MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_NEVER);
    }

    public MailBinding(HeaderFilterStrategy headerFilterStrategy, ContentTypeResolver contentTypeResolver,
                       boolean decodeFilename, boolean mapMailMessage,
                       boolean failOnDuplicateAttachment) {
        this(headerFilterStrategy, contentTypeResolver, decodeFilename, mapMailMessage, failOnDuplicateAttachment,
             MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_NEVER, MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_NEVER);
    }

    public MailBinding(HeaderFilterStrategy headerFilterStrategy, ContentTypeResolver contentTypeResolver,
                       boolean decodeFilename, boolean mapMailMessage,
                       boolean failOnDuplicateAttachment, String generateMissingAttachmentNames,
                       String handleDuplicateAttachmentNames) {
        this.headerFilterStrategy = headerFilterStrategy;
        this.contentTypeResolver = contentTypeResolver;
        this.decodeFilename = decodeFilename;
        this.mapMailMessage = mapMailMessage;
        this.failOnDuplicateAttachment = failOnDuplicateAttachment;
        this.generateMissingAttachmentNames = generateMissingAttachmentNames;
        this.handleDuplicateAttachmentNames = handleDuplicateAttachmentNames;
    }

    public boolean isFailOnDuplicateAttachment() {
        return failOnDuplicateAttachment;
    }

    public void setFailOnDuplicateAttachment(boolean failOnDuplicateAttachment) {
        this.failOnDuplicateAttachment = failOnDuplicateAttachment;
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
        String replyTo = exchange.getIn().getHeader(MailConstants.MAIL_REPLY_TO, String.class);
        if (replyTo == null) {
            replyTo = endpoint.getConfiguration().getReplyTo();
        }
        if (replyTo != null) {
            List<InternetAddress> replyToAddresses = new ArrayList<>();
            for (String reply : splitRecipients(replyTo)) {
                replyToAddresses
                        .add(asEncodedInternetAddress(reply.trim(), determineCharSet(endpoint.getConfiguration(), exchange)));
            }
            mimeMessage.setReplyTo(replyToAddresses.toArray(new InternetAddress[0]));
        }

        // must have at least one recipients otherwise we do not know where to send the mail
        if (mimeMessage.getAllRecipients() == null) {
            throw new IllegalArgumentException("The mail message does not have any recipients set.");
        }

        // set the subject if it was passed in as an option in the uri. Note: if it is in both the URI
        // and headers the headers win.
        String subject = endpoint.getConfiguration().getSubject();
        if (subject != null) {
            mimeMessage.setSubject(subject, ExchangeHelper.getCharsetName(exchange, false));
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
            if (exchange.getIn(AttachmentMessage.class).hasAttachments()) {
                appendAttachmentsFromCamel(mimeMessage, endpoint.getConfiguration(), exchange);
            } else {
                populateContentOnMimeMessage(mimeMessage, endpoint.getConfiguration(), exchange);
            }
        }
    }

    protected String determineContentType(MailConfiguration configuration, Exchange exchange) {
        // see if we got any content type set
        String contentType = configuration.getContentType();
        if (exchange.getIn().getHeader(MailConstants.MAIL_CONTENT_TYPE) != null) {
            contentType = exchange.getIn().getHeader(MailConstants.MAIL_CONTENT_TYPE, String.class);
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
        if (exchange.getIn().getHeader(MailConstants.MAIL_CONTENT_TYPE) != null) {
            contentType = exchange.getIn().getHeader(MailConstants.MAIL_CONTENT_TYPE, String.class);
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
                    LOG.warn("Charset: {} is not supported and cannot be used as charset in Content-Type header.", charset);
                    return null;
                }
            }
        }

        // Using the charset header of exchange as a fall back
        return ExchangeHelper.getCharsetName(exchange, false);
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
            if (mapMailMessage) {
                return message.getContent();
            }
            return message; // raw message
        } catch (Exception e) {
            // try to fix message in case it has an unsupported encoding in the Content-Type header
            UnsupportedEncodingException uee
                    = org.apache.camel.util.ObjectHelper.getException(UnsupportedEncodingException.class, e);
            if (uee != null) {
                LOG.debug("Unsupported encoding detected: {}", uee.getMessage());
                try {
                    String contentType = message.getContentType();
                    String type = StringHelper.before(contentType, "charset=");
                    if (type != null) {
                        // try again with fixed content type
                        LOG.debug("Trying to extract mail message again with fixed Content-Type: {}", type);
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

            throw new RuntimeCamelException(
                    "Failed to extract body due to: " + e.getMessage()
                                            + ". Exchange: " + exchange + ". Message: " + message,
                    e);
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
            LOG.trace("No attachments to extract as content is not Multipart: {}", content.getClass().getName());
        }

        LOG.trace("Extracting attachments +++ done +++");
    }

    protected void extractAttachmentsFromMultipart(Multipart mp, Map<String, Attachment> map)
            throws MessagingException, IOException {

        for (int i = 0; i < mp.getCount(); i++) {
            Part part = mp.getBodyPart(i);
            LOG.trace("Part #{}: {}", i, part);

            if (part.isMimeType("multipart/*")) {
                LOG.trace("Part #{}: is mimetype: multipart/*", i);
                extractAttachmentsFromMultipart((Multipart) part.getContent(), map);
            } else {
                String disposition = part.getDisposition();
                String fileName = part.getFileName();
                // fix file name if using malicious parameter name
                if (fileName != null) {
                    fileName = fileName.replaceAll("[\n\r\t]", "_");
                }

                if (isAttachment(disposition) && (fileName == null || fileName.isEmpty())) {
                    if (generateMissingAttachmentNames != null
                            && generateMissingAttachmentNames.equalsIgnoreCase(MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_UUID)) {
                        fileName = UUID.randomUUID().toString();
                    }
                }
                if (fileName != null && decodeFilename) {
                    fileName = MimeUtility.decodeText(fileName);
                }
                if (fileName != null) {
                    fileName = FileUtil.stripPath(fileName);
                }
                if (fileName != null) {
                    fileName = fileName.trim();
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Part #{}: Disposition: {}", i, disposition);
                    LOG.trace("Part #{}: Description: {}", i, part.getDescription());
                    LOG.trace("Part #{}: ContentType: {}", i, part.getContentType());
                    LOG.trace("Part #{}: FileName: {}", i, fileName);
                    LOG.trace("Part #{}: Size: {}", i, part.getSize());
                    LOG.trace("Part #{}: LineCount: {}", i, part.getLineCount());
                }

                if (validDisposition(disposition, fileName) || (fileName != null && !fileName.isEmpty())) {
                    LOG.debug("Mail contains file attachment: {}", fileName);
                    if (handleDuplicateAttachmentNames != null) {
                        if (handleDuplicateAttachmentNames
                                .equalsIgnoreCase(MailConstants.MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_UUID_PREFIX)) {
                            fileName = prefixDuplicateFilenames(map, fileName);
                        } else if (handleDuplicateAttachmentNames
                                .equalsIgnoreCase(MailConstants.MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_UUID_SUFFIX)) {
                            fileName = suffixDuplicateFilenames(map, fileName);
                        }
                    }
                    if (!map.containsKey(fileName)) {
                        // Parts marked with a disposition of Part.ATTACHMENT are clearly attachments
                        final DataHandler dataHandler = part.getDataHandler();
                        final DataSource dataSource = dataHandler.getDataSource();

                        final DataHandler replacement = new DataHandler(new DelegatingDataSource(fileName, dataSource));
                        DefaultAttachment camelAttachment = new DefaultAttachment(replacement);
                        @SuppressWarnings("unchecked")
                        Enumeration<Header> headers = part.getAllHeaders();
                        while (headers.hasMoreElements()) {
                            Header header = headers.nextElement();
                            camelAttachment.addHeader(header.getName(), header.getValue());
                        }
                        map.put(fileName, camelAttachment);
                    } else {
                        handleDuplicateFileAttachment(mp, fileName);
                    }
                }
            }
        }
    }

    /**
     * Strategy for handling extracting mail message that has duplicate file attachments
     *
     * @param  mp                 the multipart entity
     * @param  duplicateFileName  the duplicated file name
     * @throws MessagingException is thrown, failing with an error
     */
    protected void handleDuplicateFileAttachment(Multipart mp, String duplicateFileName) throws MessagingException {
        if (failOnDuplicateAttachment) {
            throw new MessagingException("Duplicate file attachment: " + duplicateFileName);
        } else {
            LOG.warn("Cannot extract duplicate file attachment: {}.", duplicateFileName);
        }
    }

    /**
     * Updates already existing filenames in the map and prefixes the current filename
     *
     * @param  map
     * @param  fileName
     * @return
     */
    private String prefixDuplicateFilenames(Map<String, Attachment> map, String fileName) {
        if (map.containsKey(fileName)) {
            Attachment obj = map.remove(fileName);
            map.put(prefixWithUUID(fileName), obj);
            return prefixWithUUID(fileName);
        }
        return fileName;
    }

    /**
     * Updates already existing filenames in the map and suffixes the current filename Filename will be suffixed, the
     * file extension will remain If the string starts with a dot and no further dots are contained in the string, this
     * is considered as a filename without file extension
     *
     * @param  map
     * @param  fileName
     * @return
     */
    private String suffixDuplicateFilenames(Map<String, Attachment> map, String fileName) {
        if (map.containsKey(fileName)) {
            Attachment obj = map.remove(fileName);
            map.put(suffixWithUUID(fileName), obj);
            return suffixWithUUID(fileName);
        }
        return fileName;
    }

    private String prefixWithUUID(String string) {
        return UUID.randomUUID() + "_" + string;
    }

    private String suffixWithUUID(String string) {
        if (string.lastIndexOf(".") > 0) {
            string = new StringBuilder(string).insert(string.lastIndexOf("."), "_" + UUID.randomUUID()).toString();
        } else {
            string = string + "_" + UUID.randomUUID();
        }
        return string;
    }

    private boolean validDisposition(String disposition, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        return isAttachment(disposition);
    }

    private boolean isAttachment(String disposition) {
        return disposition != null
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
                        mimeMessage.setFrom(asEncodedInternetAddress(asString(exchange, headerValue),
                                determineCharSet(configuration, exchange)));
                        continue;
                    }
                    if (headerName.equalsIgnoreCase("sender")) {
                        mimeMessage.setSender(asEncodedInternetAddress(asString(exchange, headerValue),
                                determineCharSet(configuration, exchange)));
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
                    if (isCollection(headerValue)) {
                        Iterator<?> iter = ObjectHelper.createIterator(headerValue);
                        while (iter.hasNext()) {
                            Object value = iter.next();
                            mimeMessage.addHeader(StringHelper.removeCRLF(headerName), asString(exchange, value));
                        }
                    } else {
                        mimeMessage.setHeader(StringHelper.removeCRLF(headerName), asString(exchange, headerValue));
                    }
                }
            }
        }
    }

    private void setRecipientFromCamelMessage(MimeMessage mimeMessage, MailConfiguration configuration, Exchange exchange)
            throws MessagingException, IOException {
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            if (headerValue != null && isRecipientHeader(headerName)) {
                // special handling of recipients
                if (isCollection(headerValue)) {
                    Iterator<?> iter = ObjectHelper.createIterator(headerValue);
                    while (iter.hasNext()) {
                        Object recipient = iter.next();
                        appendRecipientToMimeMessage(mimeMessage, configuration, exchange,
                                StringHelper.removeCRLF(headerName), asString(exchange, recipient));
                    }
                } else {
                    appendRecipientToMimeMessage(mimeMessage, configuration, exchange,
                            StringHelper.removeCRLF(headerName), asString(exchange, headerValue));
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
            appendRecipientToMimeMessage(mimeMessage, endpoint.getConfiguration(), exchange,
                    Message.RecipientType.TO.toString(), recipients.get(Message.RecipientType.TO));
        }
        if (recipients.containsKey(Message.RecipientType.CC)) {
            appendRecipientToMimeMessage(mimeMessage, endpoint.getConfiguration(), exchange,
                    Message.RecipientType.CC.toString(), recipients.get(Message.RecipientType.CC));
        }
        if (recipients.containsKey(Message.RecipientType.BCC)) {
            appendRecipientToMimeMessage(mimeMessage, endpoint.getConfiguration(), exchange,
                    Message.RecipientType.BCC.toString(), recipients.get(Message.RecipientType.BCC));
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
        AttachmentsContentTransferEncodingResolver contentTransferEncodingResolver
                = configuration.getAttachmentsContentTransferEncodingResolver();
        if (exchange.getIn(AttachmentMessage.class).hasAttachments()) {
            addAttachmentsToMultipart(multipart, partDisposition, contentTransferEncodingResolver, exchange);
        }
        return multipart;
    }

    protected void addAttachmentsToMultipart(
            MimeMultipart multipart, String partDisposition,
            AttachmentsContentTransferEncodingResolver encodingResolver, Exchange exchange)
            throws MessagingException {
        LOG.trace("Adding attachments +++ start +++");
        int i = 0;
        for (Map.Entry<String, Attachment> entry : exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet()) {
            String attachmentFilename = entry.getKey();
            Attachment attachment = entry.getValue();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Attachment #{}: Disposition: {}", i, partDisposition);
                LOG.trace("Attachment #{}: DataHandler: {}", i, attachment.getDataHandler());
                LOG.trace("Attachment #{}: FileName: {}", i, attachmentFilename);
            }
            if (attachment != null) {
                if (shouldAddAttachment()) {
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

                    // Perform a case insensitive "startsWith" check that works for different locales
                    String pattern = "cid";
                    if (attachmentFilename.regionMatches(true, 0, pattern, 0, pattern.length())) {
                        // add a Content-ID header to the attachment
                        // must use angle brackets according to RFC: http://www.ietf.org/rfc/rfc2392.txt
                        messageBodyPart.addHeader("Content-ID", "<" + attachmentFilename.substring(4) + ">");
                        // Set the filename without the cid
                        messageBodyPart.setFileName(attachmentFilename.substring(4));
                    } else {
                        // Set the filename
                        messageBodyPart.setFileName(attachmentFilename);
                    }

                    LOG.trace("Attachment #{}: ContentType: {}", i, messageBodyPart.getContentType());

                    if (contentTypeResolver != null) {
                        String contentType = contentTypeResolver.resolveContentType(attachmentFilename);
                        LOG.trace("Attachment #{}: Using content type resolver: {} resolved content type as: {}", i,
                                contentTypeResolver, contentType);
                        if (contentType != null) {
                            String value = contentType + "; name=" + attachmentFilename;
                            messageBodyPart.setHeader("Content-Type", value);
                            LOG.trace("Attachment #{}: ContentType: {}", i, messageBodyPart.getContentType());
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
                LOG.warn("Cannot add attachment: {} as DataHandler is null", attachmentFilename);
            }
            i++;
        }
        LOG.trace("Adding attachments +++ done +++");
    }

    protected void resolveContentTransferEncoding(
            AttachmentsContentTransferEncodingResolver resolver, int i, BodyPart messageBodyPart)
            throws MessagingException {
        if (resolver != null) {
            String contentTransferEncoding = resolver.resolveContentTransferEncoding(messageBodyPart);
            LOG.trace("Attachment #{}: Using content transfer encoding resolver: {} resolved content transfer encoding as: {}",
                    i, resolver, contentTransferEncoding);
            if (contentTransferEncoding != null) {
                messageBodyPart.setHeader("Content-Transfer-Encoding", contentTransferEncoding);
            }
        }
    }

    protected void createMultipartAlternativeMessage(
            MimeMessage mimeMessage, MailConfiguration configuration, Exchange exchange)
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
        if (!exchange.getIn(AttachmentMessage.class).hasAttachments()) {
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
                AttachmentsContentTransferEncodingResolver resolver
                        = configuration.getAttachmentsContentTransferEncodingResolver();
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
    protected boolean shouldAddAttachment() {
        return true;
    }

    protected Map<String, Object> extractHeadersFromMail(Message mailMessage, Exchange exchange)
            throws MessagingException, IOException {
        Map<String, Object> answer = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<?> names = mailMessage.getAllHeaders();

        MailConfiguration mailConfiguration = ((MailEndpoint) exchange.getFromEndpoint()).getConfiguration();
        while (names.hasMoreElements()) {
            Header header = (Header) names.nextElement();

            String value = header.getValue();
            if (value != null && mailConfiguration.isMimeDecodeHeaders()) {
                value = MimeUtility.decodeText(MimeUtility.unfold(value));
            }

            if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(header.getName(), value, exchange)) {
                CollectionHelper.appendValue(answer, header.getName(), value);
            }
        }
        // if the message is a multipart message, do not set the content type to multipart/*
        if (mapMailMessage) {
            Object content = mailMessage.getContent();
            if (content instanceof MimeMultipart) {
                MimeMultipart multipart = (MimeMultipart) content;
                int size = multipart.getCount();
                for (int i = 0; i < size; i++) {
                    BodyPart part = multipart.getBodyPart(i);
                    content = part.getContent();
                    // in case of nested multiparts iterate into them
                    while (content instanceof MimeMultipart) {
                        if (multipart.getCount() < 1) {
                            break;
                        }
                        part = ((MimeMultipart) content).getBodyPart(0);
                        content = part.getContent();
                    }
                    // Perform a case insensitive "startsWith" check that works for different locales
                    String prefix = "text";
                    if (part.getContentType().regionMatches(true, 0, prefix, 0, prefix.length())) {
                        answer.put(Exchange.CONTENT_TYPE, part.getContentType());
                        break;
                    }
                }
            }
        }

        if (mailMessage.getSentDate() != null) {
            answer.put(Exchange.MESSAGE_TIMESTAMP, mailMessage.getSentDate().getTime());
        }

        return answer;
    }

    private static void appendRecipientToMimeMessage(
            MimeMessage mimeMessage, MailConfiguration configuration, Exchange exchange,
            String type, String recipient)
            throws MessagingException, IOException {
        List<InternetAddress> recipientsAddresses = new ArrayList<>();
        for (String line : splitRecipients(recipient)) {
            String address = line.trim();
            // Only add the address which is not empty
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(address)) {
                recipientsAddresses.add(asEncodedInternetAddress(address, determineCharSet(configuration, exchange)));
            }
        }

        mimeMessage.addRecipients(asRecipientType(type),
                recipientsAddresses.toArray(new InternetAddress[0]));
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
        String strValue = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
        return StringHelper.removeCRLF(strValue);
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

    private static boolean isCollection(Object value) {
        return value instanceof Collection || value != null && value.getClass().isArray();
    }

    public String getGenerateMissingAttachmentNames() {
        return generateMissingAttachmentNames;
    }

    public void setGenerateMissingAttachmentNames(String generateMissingAttachmentNames) {
        this.generateMissingAttachmentNames = generateMissingAttachmentNames;
    }

    public String getHandleDuplicateAttachmentNames() {
        return handleDuplicateAttachmentNames;
    }

    public void setHandleDuplicateAttachmentNames(String handleDuplicateAttachmentNames) {
        this.handleDuplicateAttachmentNames = handleDuplicateAttachmentNames;
    }
}
