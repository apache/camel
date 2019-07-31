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
package net.javacrumbs.springws.test.helper;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.WebServiceMessageReceiver;

/**
 * This class allows to spring to set the property webServiceMessageReceiver from
 * the bean context. 
 * We have to use use the package net.javacrumbs.springws.test.helper to get it work
 */
public class InMemoryWebServiceMessageSender2 extends InMemoryWebServiceMessageSender {

    private WebServiceMessageReceiver decorator;

    @Override
    public WebServiceMessageReceiver getWebServiceMessageReceiver() {
        return super.getWebServiceMessageReceiver();
    }

    @Override
    public void setWebServiceMessageReceiver(WebServiceMessageReceiver webServiceMessageReceiver) {
        super.setWebServiceMessageReceiver(webServiceMessageReceiver);
    }

    public void decorateResponseReceiver() {
        final WebServiceMessageReceiver original = getWebServiceMessageReceiver();
        setWebServiceMessageReceiver(new WebServiceMessageReceiver() {

            @Override
            public void receive(MessageContext messageContext) throws Exception {
                decorator.receive(messageContext);
                original.receive(messageContext);
            }
        });
    }

    /*
     * (non-Javadoc)
     * @see net.javacrumbs.springws.test.helper.InMemoryWebServiceMessageSender#
     * afterPropertiesSet()
     */
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        if (decorator != null) {
            decorateResponseReceiver();
        }
    }

    /**
     * * @return Returns the decorator.
     */
    public WebServiceMessageReceiver getDecorator() {
        return decorator;
    }

    /**
     * @param decorator The decorator to set.
     */
    public void setDecorator(WebServiceMessageReceiver decorator) {
        this.decorator = decorator;
    }
}
