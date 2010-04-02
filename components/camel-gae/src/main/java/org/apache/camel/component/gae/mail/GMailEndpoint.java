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

import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailService.Message;

import com.google.appengine.api.mail.MailServiceFactory;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.component.gae.bind.OutboundBindingSupport;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents a <a href="http://camel.apache.org/gmail.html">Google App Engine Mail endpoint</a>.
 */
public class GMailEndpoint extends DefaultEndpoint implements OutboundBindingSupport<GMailEndpoint, Message, Void> {

    private OutboundBinding<GMailEndpoint, Message, Void> outboundBinding;
    
    private MailService mailService;
    
    private String sender;
    
    private String subject;
    
    private String to;
    
    private String cc;
    
    private String bcc;
    
    public GMailEndpoint(String endpointUri, String sender) {
        super(endpointUri);
        this.sender = sender;
        this.mailService = MailServiceFactory.getMailService();
    }
    
    public OutboundBinding<GMailEndpoint, Message, Void> getOutboundBinding() {
        return outboundBinding;
    }

    public void setOutboundBinding(OutboundBinding<GMailEndpoint, Message, Void> outboundBinding) {
        this.outboundBinding = outboundBinding;
    }
    
    public MailService getMailService() {
        return mailService;
    }

    public String getSender() {
        return sender;
    }
    
    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getBcc() {
        return bcc;
    }

    public void setBcc(String bcc) {
        this.bcc = bcc;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("consumption from gmail endpoint not supported");
    }

    public Producer createProducer() throws Exception {
        return new GMailProducer(this);
    }

    public boolean isSingleton() {
        return true;
    }

}
