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
import java.util.Map;

import javax.activation.DataHandler;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;

import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.CollectionHelper;

/**
 * Represents a {@link org.apache.camel.Message} for working with Mail
 *
 * @version $Revision:520964 $
 */
public class MailMessage extends DefaultMessage {
    private Message mailMessage;

    public MailMessage() {
    }

    public MailMessage(Message message) {
        this.mailMessage = message;
    }

    @Override
    public String toString() {
        if (mailMessage != null) {
            return "MailMessage: " + mailMessage;
        } else {
            return "MailMessage: " + getBody();
        }
    }

    @Override
    public MailExchange getExchange() {
        return (MailExchange)super.getExchange();
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

    public Object getHeader(String name) {
        String[] answer = null;
        if (mailMessage != null) {
            try {
                answer = mailMessage.getHeader(name);
            } catch (MessagingException e) {
                throw new MessageHeaderAccessException(name, e);
            }
        }
        if (answer == null) {
            return super.getHeader(name);
        }
        if (answer.length == 1) {
            return answer[0];
        }
        return answer;
    }

    @Override
    public MailMessage newInstance() {
        return new MailMessage();
    }

    @Override
    protected Object createBody() {
        if (mailMessage != null) {
            return getExchange().getBinding().extractBodyFromMail(getExchange(), mailMessage);
        }
        return null;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        if (mailMessage != null) {
            Enumeration names;
            try {
                names = mailMessage.getAllHeaders();
            } catch (MessagingException e) {
                throw new MessageHeaderNamesAccessException(e);
            }
            try {
                while (names.hasMoreElements()) {
                    Header header = (Header)names.nextElement();
                    String value = header.getValue();
                    String name = header.getName();
                    CollectionHelper.appendValue(map, name, value);
                }
            } catch (Throwable e) {
                throw new MessageHeaderNamesAccessException(e);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.apache.camel.impl.DefaultMessage#populateInitialAttachments(java.util.Map)
     */
    @Override
    protected void populateInitialAttachments(Map<String, DataHandler> map) {
        if (mailMessage != null) {
            try {
                extractAttachments(map);
            } catch (MessagingException ex) {
                throw new RuntimeMailException("Error populating the initial mail message attachments", ex);
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
     * parses the attachments of the mail message and puts them to the message
     *
     * @param map       the attachments map
     * @throws javax.mail.MessagingException
     */
    protected void extractAttachments(Map<String, DataHandler> map) throws javax.mail.MessagingException {
        // now convert the mail attachments and put it to the msg
        Multipart mp;
        Object content;

        try {
            content = this.mailMessage.getContent();

            if (content instanceof Multipart) {
                // mail with attachment
                mp = (Multipart)content;
                int nbMP = mp.getCount();
                for (int i = 0; i < nbMP; i++) {
                    Part part = mp.getBodyPart(i);
                    String disposition = part.getDisposition();

                    if (disposition != null
                        && (disposition.equalsIgnoreCase(Part.ATTACHMENT) || disposition
                            .equalsIgnoreCase(Part.INLINE))) {
                        // only add named attachments
                        if (part.getFileName() != null) {
                            // Parts marked with a disposition of
                            // Part.ATTACHMENT
                            // from part.getDisposition() are clearly
                            // attachments
                            DataHandler att = part.getDataHandler();
                            // this is clearly a attachment
                            CollectionHelper.appendValue(map, part.getFileName(), att);
                        }
                    }
                }
            }
        } catch (MessagingException e) {
            throw new javax.mail.MessagingException("Error while setting content on normalized message", e);
        } catch (IOException e) {
            throw new javax.mail.MessagingException("Error while fetching content", e);
        }
    }
}
