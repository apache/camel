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

package org.apache.camel.component.rocketmq;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.support.DefaultConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;

public class RocketMQConsumer extends DefaultConsumer implements Suspendable {

    private final RocketMQEndpoint endpoint;

    private DefaultMQPushConsumer mqPushConsumer;

    public RocketMQConsumer(RocketMQEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    private void startConsumer() throws MQClientException {
        mqPushConsumer = new DefaultMQPushConsumer(
                null, endpoint.getConsumerGroup(),
                RocketMQAclUtils.getAclRPCHook(getEndpoint().getAccessKey(), getEndpoint().getSecretKey()));
        mqPushConsumer.setNamesrvAddr(endpoint.getNamesrvAddr());
        mqPushConsumer.subscribe(endpoint.getTopicName(), endpoint.getSubscribeTags());
        mqPushConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            MessageExt messageExt = msgs.get(0);
            Exchange exchange = endpoint.createRocketExchange(messageExt.getBody());
            RocketMQMessageConverter.populateHeadersByMessageExt(exchange.getIn(), messageExt);
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException(e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
    }

    private void stopConsumer() {
        if (mqPushConsumer != null) {
            mqPushConsumer.shutdown();
            mqPushConsumer = null;
        }
    }

    @Override
    public RocketMQEndpoint getEndpoint() {
        return (RocketMQEndpoint) super.getEndpoint();
    }

    @Override
    protected void doSuspend() {
        stopConsumer();
    }

    @Override
    protected void doResume() throws Exception {
        startConsumer();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        startConsumer();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        stopConsumer();
    }
}
