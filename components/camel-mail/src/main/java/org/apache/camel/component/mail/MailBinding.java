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

/**
 * A Strategy used to convert between a Camel {@link Exchange} and {@link Message} to and
 * from a Mail {@link MimeMessage}
 *
 * @version $Revision$
 */
public class MailBinding {

    private HeaderFilterStrategy headerFilterStrategy;

    public MailBinding() {
        headerFilterStrategy = new DefaultHeaderFilterStrategy();
    }
    
    public MailBinding(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy; 
    }

    public void populateMailMessage(MailEndpoint endpoint, MimeMessage mimeMessage, Exchange exchange)
        throws MessagingException, IOException {

        // append the headers from the in message at first
        appendHeadersFromCamel(mimeMessage, exchange, exchange.getIn());

        // than override any from the fixed endpoint configuraiton
        Map<Message.RecipientType, String> recipients = endpoint.getConfiguration().getRecipients();
        if (recipients.containsKey(Message.RecipientType.TO)) {
            mimeMessage.setRecipients(Message.RecipientType.TO, recipients.get(Message.RecipientType.TO));
        }
        if (recipients.containsKey(Message.RecipientType.CC)) {
            mimeMessage.setRecipients(Message.RecipientType.CC, recipients.get(Message.RecipientType.CC));
        }
        if (recipients.containsKey(Message.RecipientType.BCC)) {
            mimeMessage.setRecipients(Message.RecipientType.BCC, recipients.get(Message.RecipientType.BCC));
        }

        // fallback to use destination if no TO provided at all
        if (mimeMessage.getRecipients(Message.RecipientType.TO) == null) {
            mimeMessage.setRecipients(Message.RecipientType.TO, endpoint.getConfiguration().getDestination());
        }

        // must have at least one recipients otherwise we do not know where to send the mail
        if (mimeMessage.getAllRecipients() == null) {
            throw new IllegalArgumentException("The mail message does not have any recipients set.");
        }

        if (empty(mimeMessage.getFrom())) {
            // lets default the address to the endpoint destination
            String from = endpoint.getConfiguration().getFrom();
            mimeMessage.setFrom(new InternetAddress(from));
        }

        if (exchange.getIn().hasAttachments()) {
            appendAttachmentsFromCamel(mimeMessage, exchange.getIn(), endpoint.getConfiguration());
        } else {
            if ("text/html".equals(endpoint.getConfiguration().getContentType())) {
                DataSource ds = new ByteArrayDataSource(exchange.getIn().getBody(String.class), "text/html");
                mimeMessage.setDataHandler(new DataHandler(ds));
            } else {
                // its just text/plain
                mimeMessage.setText(exchange.getIn().getBody(String.class));
            }
        }
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
    protected void appendHeadersFromCamel(MimeMessage mimeMessage, Exchange exchange,
                                          org.apache.camel.Message camelMessage)
        throws MessagingException {

        for (Map.Entry<String, Object> entry : camelMessage.getHeaders().entrySet()) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            if (headerValue != null) {
                if (headerFilterStrategy != null && 
                        !headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue)) {
                    // Mail messages can repeat the same header...
                    if (ObjectConverter.isCollection(headerValue)) {
                        Iterator iter = ObjectConverter.iterator(headerValue);
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

    /**
     * Does the given camel message contain any To, CC or BCC header names?
     */
    private static boolean hasRecipientHeaders(org.apache.camel.Message camelMessage) {
        for (String key : camelMessage.getHeaders().keySet()) {
            if (Message.RecipientType.TO.toString().equals(key)) {
                return true;
            } else if (Message.RecipientType.CC.toString().equals(key)) {
                return true;
            } else if (Message.RecipientType.BCC.toString().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Appends the Mail attachments from the Camel {@link MailMessage}
     */
    protected void appendAttachmentsFromCamel(MimeMessage mimeMessage, org.apache.camel.Message camelMessage,
                                              MailConfiguration configuration)
        throws MessagingException {

        // Create a Multipart
        MimeMultipart multipart = new MimeMultipart();

        // fill the body with text
        multipart.setSubType("mixed");
        MimeBodyPart textBodyPart = new MimeBodyPart();
        textBodyPart.setContent(camelMessage.getBody(String.class), configuration.getContentType());
        multipart.addBodyPart(textBodyPart);

        for (Map.Entry<String, DataHandler> entry : camelMessage.getAttachments().entrySet()) {
            String attachmentFilename = entry.getKey();
            DataHandler handler = entry.getValue();
            if (handler != null) {
                if (shouldOutputAttachment(camelMessage, attachmentFilename, handler)) {
                    // Create another body part
                    BodyPart messageBodyPart = new MimeBodyPart();
                    // Set the data handler to the attachment
                    messageBodyPart.setDataHandler(handler);
                    // Set the filename
                    messageBodyPart.setFileName(attachmentFilename);
                    // Set Disposition
                    messageBodyPart.setDisposition(Part.ATTACHMENT);
                    // Add part to multipart
                    multipart.addBodyPart(messageBodyPart);
                }
            }
        }

        // Put parts in message
        mimeMessage.setContent(multipart);
    }
    
    /**
     * Strategy to allow filtering of attachments which are put on the Mail message
     */
    protected boolean shouldOutputAttachment(org.apache.camel.Message camelMessage, String attachmentFilename, DataHandler handler) {
        return true;
    }

    private static boolean empty(Address[] addresses) {
        return addresses == null || addresses.length == 0;
    }

    private static String asString(Exchange exchange, Object value) {
        return exchange.getContext().getTypeConverter().convertTo(String.class, value);
    }

    public Map<String, Object> extractHeadersFromMail(Message mailMessage) throws MessagingException {
        Map<String, Object> answer = new HashMap<String, Object>();
        Enumeration names = mailMessage.getAllHeaders();
        
        while (names.hasMoreElements()) {
            Header header = (Header)names.nextElement();
            String[] value = mailMessage.getHeader(header.getName());
            if (headerFilterStrategy != null && 
                    !headerFilterStrategy.applyFilterToExternalHeaders(header.getName(), value)) {
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

}
