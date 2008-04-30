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

package org.apache.camel.component.spring.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;

/**
 * An {@link Exchange} for working with Spring Integration endpoints which exposes the underlying
 * Spring messages via {@link #getInMessage()} and {@link #getOutMessage()}
 *
 * @version $Revision$
 */
public class SpringIntegrationExchange  extends DefaultExchange {

    public SpringIntegrationExchange(CamelContext context) {
        super(context);
    }

    public SpringIntegrationExchange(CamelContext context, ExchangePattern pattern) {
        super(context, pattern);
    }

    @Override
    public Exchange newInstance() {
        return new SpringIntegrationExchange(this.getContext());
    }

    @Override
    public SpringIntegrationMessage getIn() {
        return (SpringIntegrationMessage) super.getIn();
    }

    @Override
    public SpringIntegrationMessage getOut() {
        return (SpringIntegrationMessage) super.getOut();
    }

    @Override
    public SpringIntegrationMessage getOut(boolean lazyCreate) {
        return (SpringIntegrationMessage) super.getOut(lazyCreate);
    }

    @Override
    public SpringIntegrationMessage getFault() {
        return (SpringIntegrationMessage) super.getFault();
    }

    @Override
    protected Message createFaultMessage() {
        return new SpringIntegrationMessage();
    }

    @Override
    protected Message createInMessage() {
        return new SpringIntegrationMessage();
    }

    @Override
    protected Message createOutMessage() {
        return new SpringIntegrationMessage();
    }

}
