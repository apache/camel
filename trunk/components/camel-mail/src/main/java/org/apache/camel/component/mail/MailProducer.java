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

import javax.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

/**
 * A Producer to send messages using JavaMail.
 *  
 * @version 
 */
public class MailProducer extends DefaultProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(MailProducer.class);
    private final JavaMailSender sender;

    public MailProducer(MailEndpoint endpoint, JavaMailSender sender) {
        super(endpoint);
        this.sender = sender;
    }

    public void process(final Exchange exchange) {
        sender.send(new MimeMessagePreparator() {
            public void prepare(MimeMessage mimeMessage) throws Exception {
                getEndpoint().getBinding().populateMailMessage(getEndpoint(), mimeMessage, exchange);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending MimeMessage: " + MailUtils.dumpMessage(mimeMessage));
                }
            }
        });
    }
    
    @Override
    public MailEndpoint getEndpoint() {
        return (MailEndpoint) super.getEndpoint();
    }
}
