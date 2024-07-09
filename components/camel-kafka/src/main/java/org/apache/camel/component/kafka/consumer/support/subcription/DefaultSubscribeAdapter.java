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
package org.apache.camel.component.kafka.consumer.support.subcription;

import org.apache.camel.component.kafka.consumer.support.TopicHelper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSubscribeAdapter implements SubscribeAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscribeAdapter.class);

    @Override
    public void subscribe(Consumer<?, ?> consumer, ConsumerRebalanceListener reBalanceListener, TopicInfo topicInfo) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Subscribing to {}", TopicHelper.getPrintableTopic(topicInfo));
        }

        if (topicInfo.isPattern()) {
            consumer.subscribe(topicInfo.getPattern(), reBalanceListener);
        } else {
            consumer.subscribe(topicInfo.getTopics(), reBalanceListener);
        }
    }
}
