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

import org.apache.camel.Exchange;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.DefaultProducer;

public class GMailProducer extends DefaultProducer {

    public GMailProducer(GMailEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public GMailEndpoint getEndpoint() {
        return (GMailEndpoint)super.getEndpoint();
    }
    
    public OutboundBinding<GMailEndpoint, Message, Void> getOutboundBinding() {
        return getEndpoint().getOutboundBinding();
    }
    
    public MailService getMailService() {
        return getEndpoint().getMailService();
    }

    /**
     * Invokes the mail service.
     * 
     * @param exchange contains the mail data in the in-message.
     * @see GMailBinding
     */
    public void process(Exchange exchange) throws Exception {
        getMailService().send(getOutboundBinding().writeRequest(getEndpoint(), exchange, null));
    }

}
