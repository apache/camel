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
package org.apache.camel.component.jms.reply;

import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.jms.SimpleJmsMessageListenerContainer;

/**
 * This {@link SimpleJmsMessageListenerContainer} is used for reply queues which are shared.
 * <p/>
 * This implementation supports using a fixed or dynamic JMS Message Selector to pickup the designated reply messages
 * from the shared queue. Since the queue is shared, then we can only pickup the reply messages which is intended for
 * us, so to support that we must use JMS Message Selectors.
 * <p/>
 * See more details at <a href="http://camel.apache.org/jms">camel-jms</a>.
 *
 * @see ExclusiveQueueSimpleMessageListenerContainer
 */
public class SharedQueueSimpleMessageListenerContainer extends SimpleJmsMessageListenerContainer {

    private String fixedMessageSelector;
    private MessageSelectorCreator creator;

    /**
     * Use a fixed JMS message selector
     *
     * @param endpoint             the endpoint
     * @param fixedMessageSelector the fixed selector
     */
    public SharedQueueSimpleMessageListenerContainer(JmsEndpoint endpoint, String fixedMessageSelector) {
        super(endpoint);
        this.fixedMessageSelector = fixedMessageSelector;
    }

    /**
     * Use a dynamic JMS message selector
     *
     * @param endpoint the endpoint
     * @param creator  the creator to create the dynamic selector
     */
    public SharedQueueSimpleMessageListenerContainer(JmsEndpoint endpoint, MessageSelectorCreator creator) {
        super(endpoint);
        this.creator = creator;
    }

    // override this method and return the appropriate selector
    @Override
    public String getMessageSelector() {
        return JmsReplyHelper.getMessageSelector(fixedMessageSelector, creator);
    }

}
