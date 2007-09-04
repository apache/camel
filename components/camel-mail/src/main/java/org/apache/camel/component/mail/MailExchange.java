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

import javax.mail.Message;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;

/**
 * Represents an {@ilnk Exchange} for working with Mail
 *
 * @version $Revision:520964 $
 */
public class MailExchange extends DefaultExchange {
    private MailBinding binding;

    public MailExchange(CamelContext context, ExchangePattern pattern, MailBinding binding) {
        super(context, pattern);
        this.binding = binding;
    }

    public MailExchange(CamelContext context, ExchangePattern pattern, MailBinding binding, Message message) {
        this(context, pattern, binding);
        setIn(new MailMessage(message));
    }

    @Override
    public MailMessage getIn() {
        return (MailMessage) super.getIn();
    }

    @Override
    public MailMessage getOut() {
        return (MailMessage) super.getOut();
    }

    @Override
    public MailMessage getOut(boolean lazyCreate) {
        return (MailMessage) super.getOut(lazyCreate);
    }

    @Override
    public MailMessage getFault() {
        return (MailMessage) super.getFault();
    }

    public MailBinding getBinding() {
        return binding;
    }

    @Override
    public Exchange newInstance() {
        return new MailExchange(getContext(), getPattern(), binding);
    }

    // Expose Email APIs
    //-------------------------------------------------------------------------

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected MailMessage createInMessage() {
        return new MailMessage();
    }

    @Override
    protected MailMessage createOutMessage() {
        return new MailMessage();
    }
}
