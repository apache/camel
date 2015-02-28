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
package org.apache.camel.component.redis;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@UriParams
public class RedisConfiguration {
    @UriPath @Metadata(required = "true")
    private String host;
    @UriPath @Metadata(required = "true")
    private Integer port;
    @UriParam
    private String command;
    @UriParam
    private String channels;
    @UriParam
    private RedisTemplate redisTemplate;
    @UriParam
    private RedisMessageListenerContainer listenerContainer;
    @UriParam
    private RedisConnectionFactory connectionFactory;
    @UriParam
    private RedisSerializer serializer;
    @UriParam
    private boolean managedListenerContainer;
    @UriParam
    private boolean managedConnectionFactory;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public RedisTemplate getRedisTemplate() {
        return redisTemplate != null ? redisTemplate : createDefaultTemplate();
    }

    public void setRedisTemplate(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RedisMessageListenerContainer getListenerContainer() {
        return listenerContainer != null ? listenerContainer : createDefaultListenerContainer();
    }

    public void setListenerContainer(RedisMessageListenerContainer listenerContainer) {
        this.listenerContainer = listenerContainer;
    }

    public String getChannels() {
        return channels;
    }

    public void setChannels(String channels) {
        this.channels = channels;
    }

    public void setConnectionFactory(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public RedisConnectionFactory getConnectionFactory() {
        return connectionFactory != null ? connectionFactory : createDefaultConnectionFactory();
    }

    public RedisSerializer getSerializer() {
        return serializer != null ? serializer : createDefaultSerializer();
    }

    public void setSerializer(RedisSerializer serializer) {
        this.serializer = serializer;
    }

    private RedisConnectionFactory createDefaultConnectionFactory() {
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory();
        managedConnectionFactory = true;

        if (host != null) {
            jedisConnectionFactory.setHostName(host);
        }
        if (port != null) {
            jedisConnectionFactory.setPort(port);
        }
        jedisConnectionFactory.afterPropertiesSet();
        connectionFactory = jedisConnectionFactory;
        return jedisConnectionFactory;
    }

    private RedisTemplate createDefaultTemplate() {
        redisTemplate = new RedisTemplate();
        redisTemplate.setDefaultSerializer(getSerializer());
        redisTemplate.setConnectionFactory(getConnectionFactory());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    private RedisMessageListenerContainer createDefaultListenerContainer() {
        listenerContainer = new RedisMessageListenerContainer();
        managedListenerContainer = true;
        listenerContainer.setConnectionFactory(getConnectionFactory());
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
        return listenerContainer;
    }

    private RedisSerializer createDefaultSerializer() {
        serializer = new JdkSerializationRedisSerializer();
        return serializer;
    }

    public void stop() throws Exception {
        if (managedConnectionFactory) {
            ((JedisConnectionFactory)connectionFactory).destroy();
        }
        if (managedListenerContainer) {
            listenerContainer.destroy();
        }
    }
}
