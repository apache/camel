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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@UriParams
public class RedisConfiguration {
    private boolean managedListenerContainer;
    private boolean managedConnectionFactory;

    @UriPath(description = "The host name or IP address of the Redis server. The endpoint connects to it with the"
                           + " JedisConnectionFactory that it creates when connectionFactory is not set, and does not use it"
                           + " otherwise. If not set, localhost is used.")
    @Metadata(required = true)
    private String host;
    @UriPath(description = "The port of the Redis server. The endpoint connects to it with the JedisConnectionFactory that"
                           + " it creates when connectionFactory is not set, and does not use it otherwise. If not set, 6379"
                           + " is used.")
    @Metadata(required = true)
    private Integer port;
    @UriParam(defaultValue = "SET",
              description = "The Redis command that the producer runs, such as GET, SET or HSET. The CamelRedis.Command"
                            + " header replaces this option for a message. The consumer needs SUBSCRIBE or PSUBSCRIBE and"
                            + " fails to start with any other command, including the default SET. The producer fails a"
                            + " message with SUBSCRIBE, PSUBSCRIBE or a server command that it does not run, such as AUTH,"
                            + " FLUSHDB, INFO or SELECT.")
    private Command command = Command.SET;
    @UriParam(description = "The channels that the consumer subscribes to, separated by comma. With the PSUBSCRIBE command"
                            + " they are patterns, such as news.*, and with SUBSCRIBE channel names. Required by the"
                            + " consumer. The producer does not use it: PUBLISH takes the channel from the"
                            + " CamelRedis.Channel header.")
    private String channels;
    @UriParam(description = "The org.springframework.data.redis.core.RedisTemplate that the producer runs the commands"
                            + " with, which also decides how keys and values are serialized. If not set, the redisTemplate"
                            + " of the component is used, and if that is not set either, the endpoint creates a"
                            + " RedisTemplate from connectionFactory and serializer. The consumer does not use it.")
    private RedisTemplate<?, ?> redisTemplate;
    @UriParam(label = "consumer,advanced",
              description = "The org.springframework.data.redis.listener.RedisMessageListenerContainer that the consumer"
                            + " subscribes to the channels with. If not set, the endpoint creates and starts one on"
                            + " connectionFactory, and destroys it when the endpoint shuts down.")
    private RedisMessageListenerContainer listenerContainer;
    @UriParam(description = "The org.springframework.data.redis.connection.RedisConnectionFactory that connects to Redis."
                            + " The endpoint uses it for the RedisTemplate and the listener container that it creates, and"
                            + " then does not use host and port. If not set, the endpoint creates a JedisConnectionFactory"
                            + " for host and port, and destroys it when the endpoint shuts down.")
    private RedisConnectionFactory connectionFactory;
    @UriParam(description = "The org.springframework.data.redis.serializer.RedisSerializer for keys and values. The"
                            + " endpoint sets it as the default serializer of the RedisTemplate that it creates, and the"
                            + " consumer deserializes the body of each message with it. If not set, a"
                            + " JdkSerializationRedisSerializer is used, which stores keys and values in Java serialized"
                            + " form: use a StringRedisSerializer to work with text. The producer does not use it when a"
                            + " redisTemplate is set.")
    private RedisSerializer<?> serializer;
    @UriParam(label = "advanced,security",
              description = "An ObjectInputFilter pattern, in jdk.serialFilter syntax, that the default"
                            + " JdkSerializationRedisSerializer applies when it deserializes a value: the body of a message"
                            + " that the consumer receives, and a value that the producer reads with the RedisTemplate that"
                            + " the endpoint creates. If not set, the JVM-wide jdk.serialFilter is used when present,"
                            + " otherwise a default filter that rejects java.net.**, allows only java.**, javax.** and"
                            + " org.apache.camel.**, and limits the depth, references and size of the object graph. Not"
                            + " used when serializer is set.")
    private String deserializationFilter;

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
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

    public RedisTemplate<?, ?> getRedisTemplate() {
        return redisTemplate != null ? redisTemplate : createDefaultTemplate();
    }

    public void setRedisTemplate(RedisTemplate<?, ?> redisTemplate) {
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

    public RedisSerializer<?> getSerializer() {
        return serializer != null ? serializer : createDefaultSerializer();
    }

    public void setSerializer(RedisSerializer<?> serializer) {
        this.serializer = serializer;
    }

    public String getDeserializationFilter() {
        return deserializationFilter;
    }

    public void setDeserializationFilter(String deserializationFilter) {
        this.deserializationFilter = deserializationFilter;
    }

    private RedisConnectionFactory createDefaultConnectionFactory() {
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory();
        managedConnectionFactory = true;

        if (host != null) {
            jedisConnectionFactory.getStandaloneConfiguration().setHostName(host);
        }
        if (port != null) {
            jedisConnectionFactory.getStandaloneConfiguration().setPort(port);
        }
        jedisConnectionFactory.afterPropertiesSet();
        connectionFactory = jedisConnectionFactory;
        return jedisConnectionFactory;
    }

    private RedisTemplate<?, ?> createDefaultTemplate() {
        redisTemplate = new RedisTemplate<>();
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

    private RedisSerializer<?> createDefaultSerializer() {
        // JdkSerializationRedisSerializer exposes no hook to install an ObjectInputFilter, so supply a deserializing
        // converter that sets one on the stream. Serialization is left untouched.
        serializer = new JdkSerializationRedisSerializer(
                new SerializingConverter(), new DeserializingConverter(new FilteringDeserializer(deserializationFilter)));
        return serializer;
    }

    public void stop() throws Exception {
        if (managedConnectionFactory) {
            ((JedisConnectionFactory) connectionFactory).destroy();
        }
        if (managedListenerContainer) {
            listenerContainer.destroy();
        }
    }
}
