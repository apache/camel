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
package org.apache.camel.component.redis;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.Topic;

public class RedisConsumer extends DefaultConsumer implements MessageListener {
    private final RedisConfiguration redisConfiguration;

    public RedisConsumer(RedisEndpoint redisEndpoint, Processor processor,
                         RedisConfiguration redisConfiguration) {
        super(redisEndpoint, processor);
        this.redisConfiguration = redisConfiguration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Collection<Topic> topics = toTopics(redisConfiguration.getChannels());
        redisConfiguration.getListenerContainer().addMessageListener(this, topics);
    }

    @Override
    protected void doStop() throws Exception {
        Collection<Topic> topics = toTopics(redisConfiguration.getChannels());
        redisConfiguration.getListenerContainer().removeMessageListener(this, topics);
        super.doStop();
    }

    private Collection<Topic> toTopics(String channels) {
        String[] channelsArrays = channels.split(",");
        List<Topic> topics = new ArrayList<>();
        for (String channel : channelsArrays) {
            String name = channel.trim();
            if (Command.PSUBSCRIBE.equals(redisConfiguration.getCommand())) {
                topics.add(new PatternTopic(name));
            } else if (Command.SUBSCRIBE.equals(redisConfiguration.getCommand())) {
                topics.add(new ChannelTopic(name));
            } else {
                throw new IllegalArgumentException("Unsupported Command " + redisConfiguration.getCommand());
            }
        }
        return topics;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Exchange exchange = getEndpoint().createExchange();
            setChannel(exchange, message.getChannel());
            setPattern(exchange, pattern);
            setBody(exchange, message.getBody());
            getProcessor().process(exchange);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setBody(Exchange exchange, byte[] body) {
        if (body != null) {
            exchange.getIn().setBody(redisConfiguration.getSerializer().deserialize(body));
        }
    }

    private void setPattern(Exchange exchange, byte[] pattern) {
        if (pattern != null) {
            exchange.getIn().setHeader(RedisConstants.PATTERN, pattern);
        }
    }

    private void setChannel(Exchange exchange, byte[] message) throws UnsupportedEncodingException {
        if (message != null) {
            exchange.getIn().setHeader(RedisConstants.CHANNEL, new String(message, StandardCharsets.UTF_8));
        }
    }
}
