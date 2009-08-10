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
import java.util.Map;

import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Represents a {@link org.apache.camel.Message} for working with Mail
 *
 * @version $Revision:520964 $
 */
public class MailMessage extends DefaultMessage {
    private static final transient Log LOG = LogFactory.getLog(MailMessage.class);
    private Message mailMessage;

    public MailMessage() {
    }

    public MailMessage(Message message) {
        this.mailMessage = message;
    }

    @Override
    public String toString() {
        if (mailMessage != null) {
            return "MailMessage: " + MailUtils.dumpMessage(mailMessage);
        } else {
            return "MailMessage: " + getBody();
        }
    }

    public MailMessage copy() {
        MailMessage answer = (MailMessage)super.copy();
        answer.mailMessage = mailMessage;
        return answer;
    }

    /**
     * Returns the underlying Mail message
     */
    public Message getMessage() {
        return mailMessage;
    }

    public void setMessage(Message mailMessage) {
        this.mailMessage = mailMessage;
    }

    @Override
    public MailMessage newInstance() {
        return new MailMessage();
    }

    @Override
    protected Object createBody() {
        if (mailMessage != null) {
            MailBinding binding = ExchangeHelper.getBinding(getExchange(), MailBinding.class);
            return binding != null ? binding.extractBodyFromMail(getExchange(), mailMessage) : null;
        }
        return null;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        if (mailMessage != null) {
            try {
                MailBinding binding = ExchangeHelper.getBinding(getExchange(), MailBinding.class);
                if (binding != null) {
                    map.putAll(binding.extractHeadersFromMail(mailMessage, getExchange()));
                }
            } catch (MessagingException e) {
                throw new RuntimeCamelException("Error accessing headers due to: " + e.getMessage(), e);
            }
        }
    }

    @Override
    protected void populateInitialAttachments(Map<String, DataHandler> map) {
        if (mailMessage != null) {
            try {
                extractAttachments(mailMessage, map);
            } catch (Exception e) {
                throw new RuntimeCamelException("Error populating the initial mail message attachments", e);
            }
        }
    }

    public void copyFrom(org.apache.camel.Message that) {
        super.copyFrom(that);
        if (that instanceof MailMessage) {
            MailMessage mailMessage = (MailMessage) that;
            this.mailMessage = mailMessage.mailMessage;
        }
    }

    /**
     * Parses the attachments of the given mail message and adds them to the map
     *
     * @param  message  the mail message with attachments
     * @param  map      the map to add found attachments (attachmentFilename is the key)
     */
    protected static void extractAttachments(Message message, Map<String, DataHandler> map)
        throws javax.mail.MessagingException, IOException {

        LOG.trace("Extracting attachments +++ start +++");

        Object content = message.getContent();
        if (content instanceof Multipart) {
            extractFromMultipart((Multipart)content, map);
        } else if (content != null) {
            LOG.trace("No attachments to extract as content is not Multipart: " + content.getClass().getName());
        }

        LOG.trace("Extracting attachments +++ done +++");
    }
    
    protected static void extractFromMultipart(Multipart mp, Map<String, DataHandler> map) 
        throws javax.mail.MessagingException, IOException {

        for (int i = 0; i < mp.getCount(); i++) {
            Part part = mp.getBodyPart(i);
            LOG.trace("Part #" + i + ": " + part);

            if (part.isMimeType("multipart/*")) {
                LOG.trace("Part #" + i + ": is mimetype: multipart/*");
                extractFromMultipart((Multipart)part.getContent(), map);
            } else {
                String disposition = part.getDisposition();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Part #" + i + ": Disposition: " + part.getDisposition());
                    LOG.trace("Part #" + i + ": Description: " + part.getDescription());
                    LOG.trace("Part #" + i + ": ContentType: " + part.getContentType());
                    LOG.trace("Part #" + i + ": FileName: " + part.getFileName());
                    LOG.trace("Part #" + i + ": Size: " + part.getSize());
                    LOG.trace("Part #" + i + ": LineCount: " + part.getLineCount());
                }

                if (disposition != null && (disposition.equalsIgnoreCase(Part.ATTACHMENT) || disposition.equalsIgnoreCase(Part.INLINE))) {
                    // only add named attachments
                    String fileName = part.getFileName();
                    if (fileName != null) {
                        LOG.debug("Mail contains file attachment: " + fileName);
                        // Parts marked with a disposition of Part.ATTACHMENT are clearly attachments
                        CollectionHelper.appendValue(map, fileName, part.getDataHandler());
                    }
                }
            }
        }
    }    

}
