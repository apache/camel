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
package org.apache.camel.component.cm;

import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import org.apache.camel.Exchange;
import org.apache.camel.component.cm.client.SMSMessage;
import org.apache.camel.component.cm.exceptions.HostUnavailableException;
import org.apache.camel.component.cm.exceptions.InvalidPayloadException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * is the exchange processor. Sends a validated sms message to CM Endpoints.
 */
public class CMProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CMProducer.class);

    private Validator validator;

    /**
     * sends a valid message to CM endpoints.
     */
    private CMSender sender;

    public CMProducer(final CMEndpoint endpoint, final CMSender sender) {
        super(endpoint);
        this.sender = sender;
    }

    /**
     * Producer is a exchange processor. This process is built in several steps. 1. Validate message receive from client 2. Send validated message to CM endpoints. 3. Process response from CM
     * endpoints.
     */
    @Override
    public void process(final Exchange exchange) {

        try {

            // Immutable message receive from clients.
            final SMSMessage smsMessage = exchange.getIn().getBody(SMSMessage.class);
            if (smsMessage == null) {
                throw new NullPointerException();
            }

            // Validates Payload - SMSMessage
            LOG.trace("Validating SMSMessage instance provided: {}", smsMessage.toString());
            final Set<ConstraintViolation<SMSMessage>> constraintViolations = getValidator().validate(smsMessage);
            if (constraintViolations.size() > 0) {
                final StringBuffer msg = new StringBuffer();
                for (final ConstraintViolation<SMSMessage> cv : constraintViolations) {
                    msg.append(String.format("- Invalid value for %s: %s", cv.getPropertyPath().toString(), cv.getMessage()));
                }
                throw new InvalidPayloadException(msg.toString());
            }
            LOG.trace("SMSMessage instance is valid: {}", smsMessage.toString());

            // We have a valid (immutable) SMSMessage instance, lets extend to CMMessage
            // This is the instance we will use to build the XML document to be
            // sent to CM SMS GW.
            final CMMessage cmMessage = new CMMessage(smsMessage.getPhoneNumber(), smsMessage.getMessage());
            LOG.debug("CMMessage instance build from valid SMSMessage instance");

            if (smsMessage.getFrom() == null || smsMessage.getFrom().isEmpty()) {
                String df = getConfiguration().getDefaultFrom();
                cmMessage.setSender(df);
                LOG.debug("Dynamic sender is set to default dynamic sender: {}", df);
            }

            // Remember, this can be null.
            cmMessage.setIdAsString(smsMessage.getId());

            // Unicode and multipart
            cmMessage.setUnicodeAndMultipart(getConfiguration().getDefaultMaxNumberOfParts());

            // 2. Send a validated sms message to CM endpoints
            // throws MessagingException for abnormal situations.
            sender.send(cmMessage);

            log.debug("Request accepted by CM Host: {}", cmMessage.toString());
        } catch (final NullPointerException e) {
            // Body hast to be an instance of SMSMessage
            final String m = "Check Message body - Has to be an SMSMessage instance";
            log.error(m, e);
            exchange.setException(new InvalidPayloadException(m));
        } catch (final RuntimeException e) {
            log.error("Message cannot be sent ", e);
            exchange.setException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {

        // log at debug level for singletons, for prototype scoped log at trace
        // level to not spam logs

        log.debug("Starting CMProducer");

        final CMConfiguration configuration = getConfiguration();

        if (configuration.isTestConnectionOnStartup()) {
            try {
                log.debug("Checking connection - {}", getEndpoint().getCMUrl());
                HttpClientBuilder.create().build().execute(new HttpHead(getEndpoint().getCMUrl()));
                log.info("Connection to {}: OK", getEndpoint().getCMUrl());
            } catch (final Exception e) {
                log.info("Connection to {}: NOT AVAILABLE", getEndpoint().getCMUrl());
                throw new HostUnavailableException(e);
            }
        }

        // keep starting
        super.doStart();

        log.info("CMProducer started");
    }

    @Override
    public CMEndpoint getEndpoint() {
        return (CMEndpoint) super.getEndpoint();
    }

    public CMConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    public Validator getValidator() {
        if (validator == null) {
            validator = getEndpoint().getComponent().getValidator();
        }
        return validator;
    }

    public CMSender getSender() {
        return sender;
    }

    public void setSender(CMSender sender) {
        this.sender = sender;
    }

}
