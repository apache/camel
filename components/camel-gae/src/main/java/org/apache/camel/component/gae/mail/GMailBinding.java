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
package org.apache.camel.component.gae.mail;

import com.google.appengine.api.mail.MailService.Message;

import org.apache.camel.Exchange;
import org.apache.camel.component.gae.bind.OutboundBinding;

/**
 * Binds the {@link Message} of the mail service to a Camel {@link Exchange}.
 */
public class GMailBinding implements OutboundBinding<GMailEndpoint, Message, Void> {

    /**
     * Camel header for setting the mail message subject.
     */
    public static final String GMAIL_SUBJECT = "org.apache.camel.component.gae.mail.Subject";
    
    /**
     * Camel header for setting the mail message recipient (list).
     */
    public static final String GMAIL_TO = "org.apache.camel.component.gae.mail.To";
    
    /**
     * Reads data from <code>exchange</code> and writes it to a newly created
     * {@link Message} instance. The <code>request</code> parameter is
     * ignored.
     * 
     * @param endpoint
     * @param exchange
     * @param request
     *            ignored.
     * @return a newly created {@link Message} instance containing data from
     *         <code>exchange</code>.
     */
    public Message writeRequest(GMailEndpoint endpoint, Exchange exchange, Message request) {
        Message message = new Message();
        writeFrom(endpoint, exchange, message);
        writeTo(endpoint, exchange, message);
        writeSubject(endpoint, exchange, message);
        writeBody(endpoint, exchange, message);
        writeAttachments(endpoint, exchange, message);
        return message;
    }

    /**
     * @throws UnsupportedOperationException.
     */
    public Exchange readResponse(GMailEndpoint endpoint, Exchange exchange, Void response) {
        throw new UnsupportedOperationException("gmail responses not supported");
    }

    protected void writeFrom(GMailEndpoint endpoint, Exchange exchange, Message request) {
        request.setSender(endpoint.getSender());
    }
    
    protected void writeTo(GMailEndpoint endpoint, Exchange exchange, Message request) {
        // TODO: support comma-separated list of receivers
        String to = (String)exchange.getIn().getHeader(GMAIL_TO);
        if (to == null) {
            to = endpoint.getTo();
        }
        request.setTo(to);
    }
    
    protected void writeSubject(GMailEndpoint endpoint, Exchange exchange, Message request) {
        String subject = (String)exchange.getIn().getHeader(GMAIL_SUBJECT);
        if (subject == null) {
            subject = endpoint.getSubject();
        }
        request.setSubject(subject);
    }
    
    protected void writeBody(GMailEndpoint endpoint, Exchange exchange, Message request) {
        // TODO: allow message header or endpoint uri to configure character encoding
        request.setTextBody(exchange.getIn().getBody(String.class));
    }
    
    protected void writeAttachments(GMailEndpoint endpoint, Exchange exchange, Message request) {
        // TODO: support attachments
    }
    
}
