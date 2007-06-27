/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.irc;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultExchange;

public class IrcExchange extends DefaultExchange {
    private IrcBinding binding;

    public IrcExchange(CamelContext context, IrcBinding binding) {
        super(context);
        this.binding = binding;
    }

    public IrcExchange(CamelContext context, IrcBinding binding, IrcMessage inMessage) {
        this(context, binding);
        setIn(inMessage);
    }

    public IrcBinding getBinding() {
        return binding;
    }

    public void setBinding(IrcBinding binding) {
        this.binding = binding;
    }

    @Override
    public IrcMessage getIn() {
        return (IrcMessage) super.getIn();
    }

    @Override
    public IrcMessage getOut() {
        return (IrcMessage) super.getOut();
    }

    @Override
    public IrcMessage getOut(boolean lazyCreate) {
        return (IrcMessage) super.getOut(lazyCreate);
    }

    @Override
    public IrcMessage getFault() {
        return (IrcMessage) super.getFault();
    }

    @Override
    public IrcExchange newInstance() {
        return new IrcExchange(getContext(), getBinding());
    }

    @Override
    protected IrcMessage createInMessage() {
        return new IrcMessage();
    }

    @Override
    protected IrcMessage createOutMessage() {
        return new IrcMessage();
    }
}
