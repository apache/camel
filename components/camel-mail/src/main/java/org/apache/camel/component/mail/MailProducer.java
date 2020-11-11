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
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer to send messages using JavaMail.
 */
public class MailProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MailProducer.class);

    private final JavaMailSender defaultSender;

    public MailProducer(MailEndpoint endpoint, JavaMailSender sender) {
        super(endpoint);
        this.defaultSender = sender;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        JavaMailSender mailSender = getSender(exchange);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader applicationClassLoader = getEndpoint().getCamelContext().getApplicationContextClassLoader();
            if (applicationClassLoader != null) {
                Thread.currentThread().setContextClassLoader(applicationClassLoader);
            }

            MimeMessage mimeMessage;

            final Object body = exchange.getIn().getBody();
            if (body instanceof MimeMessage) {
                // Body is directly a MimeMessage
                mimeMessage = (MimeMessage) body;
            } else {
                // Create a message with exchange data
                mimeMessage = new MimeMessage(mailSender.getSession());
                getEndpoint().getBinding().populateMailMessage(getEndpoint(), mimeMessage, exchange);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending MimeMessage: {}", MailUtils.dumpMessage(mimeMessage));
            }
            mailSender.send(mimeMessage);
            // set the message ID for further processing
            exchange.getIn().setHeader(MailConstants.MAIL_MESSAGE_ID, mimeMessage.getMessageID());
        } catch (MessagingException | IOException e) {
            exchange.setException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }

        callback.done(true);
        return true;
    }

    @Override
    public MailEndpoint getEndpoint() {
        return (MailEndpoint) super.getEndpoint();
    }

    protected JavaMailSender getSender(Exchange exchange) {
        // do we have special headers
        Map<String, Object> additional = URISupport.extractProperties(exchange.getMessage().getHeaders(), "mail.smtp.");
        if (additional.isEmpty()) {
            // no then use default sender
            LOG.trace("Using default JavaMailSender");
            return defaultSender;
        } else {
            // create new mail sender specially for this
            LOG.debug("Creating new JavaMailSender to include additional {} java mail properties", additional.size());
            JavaMailSender customSender = getEndpoint().getConfiguration().createJavaMailSender();
            additional.forEach((k, v) -> {
                if (v != null) {
                    // add with prefix so we dont loose that
                    customSender.addAdditionalJavaMailProperty("mail.smtp." + k, v.toString());
                }
            });
            return customSender;
        }
    }
}
