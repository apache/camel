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
package org.apache.camel.component.jms;

import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;

import org.springframework.jms.core.JmsOperations;

/**
 * A class which represents some metadata about the underlying JMS provider
 * so that we can properly bridge JMS providers such as for dealing with temporary destinations.
 */
public class JmsProviderMetadata {
    private Class<? extends TemporaryQueue> temporaryQueueType;
    private Class<? extends TemporaryTopic> temporaryTopicType;

    /**
     * Lazily loads the temporary queue type if one has not been explicitly configured
     * via calling the {@link #setTemporaryQueueType(Class)}
     */
    public Class<? extends TemporaryQueue> getTemporaryQueueType(JmsOperations template) {
        Class<? extends TemporaryQueue> answer = getTemporaryQueueType();
        if (answer == null) {
            loadTemporaryDestinationTypes(template);
            answer = getTemporaryQueueType();
        }
        return answer;
    }

    /**
     * Lazily loads the temporary topic type if one has not been explicitly configured
     * via calling the {@link #setTemporaryTopicType(Class)}
     */
    public Class<? extends TemporaryTopic> getTemporaryTopicType(JmsOperations template) {
        Class<? extends TemporaryTopic> answer = getTemporaryTopicType();
        if (answer == null) {
            loadTemporaryDestinationTypes(template);
            answer = getTemporaryTopicType();
        }
        return answer;
    }

    // Properties
    //-------------------------------------------------------------------------

    public Class<? extends TemporaryQueue> getTemporaryQueueType() {
        return temporaryQueueType;
    }

    public void setTemporaryQueueType(Class<? extends TemporaryQueue> temporaryQueueType) {
        this.temporaryQueueType = temporaryQueueType;
    }

    public Class<? extends TemporaryTopic> getTemporaryTopicType() {
        return temporaryTopicType;
    }

    public void setTemporaryTopicType(Class<? extends TemporaryTopic> temporaryTopicType) {
        this.temporaryTopicType = temporaryTopicType;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void loadTemporaryDestinationTypes(JmsOperations template) {
        if (template == null) {
            throw new IllegalArgumentException("No JmsTemplate supplied!");
        }
        template.execute(session -> {
            TemporaryQueue queue = session.createTemporaryQueue();
            setTemporaryQueueType(queue.getClass());

            TemporaryTopic topic = session.createTemporaryTopic();
            setTemporaryTopicType(topic.getClass());

            queue.delete();
            topic.delete();
            return null;
        });
    }
}
