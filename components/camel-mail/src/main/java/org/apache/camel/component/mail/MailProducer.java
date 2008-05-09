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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

/**
 * A Producer to send messages using JavaMail.
 *  
 * @version $Revision$
 */
public class MailProducer extends DefaultProducer<MailExchange> {
    private static final transient Log LOG = LogFactory.getLog(MailProducer.class);
    private final MailEndpoint endpoint;
    private final JavaMailSender sender;

    public MailProducer(MailEndpoint endpoint, JavaMailSender sender) {
        super(endpoint);
        this.endpoint = endpoint;
        this.sender = sender;
    }

    public void process(final Exchange exchange) {
        sender.send(new MimeMessagePreparator() {
            public void prepare(MimeMessage mimeMessage) throws Exception {
                endpoint.getBinding().populateMailMessage(endpoint, mimeMessage, exchange);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending MimeMessage: " + MailUtils.dumpMessage(mimeMessage));
                }
            }
        });
    }
}
