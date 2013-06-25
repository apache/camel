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
package org.apache.camel.component.netty.http;

import org.jboss.netty.channel.ChannelHandler;

/**
 * Factory for setting up Netty {@link ChannelHandler} bound to a given Netty port.
 * <p/>
 * This factory allows for consumers to reuse existing {@link org.jboss.netty.bootstrap.ServerBootstrap} which
 * allows to share the same port for multiple consumers.
 *
 * This factory is needed to ensure we can handle the situations when consumers is added and removing in
 * a dynamic environment such as OSGi, where Camel applications can be hot-deployed. And we want these
 * Camel applications to be able to share the same Netty port in a easy way.
 */
public interface HttpServerConsumerChannelFactory {

    /**
     * Initializes this consumer channel factory with the given port.
     */
    void init(int port);

    /**
     * The port number this consumer channel factory is using.
     */
    int getPort();

    /**
     * Adds the given consumer.
     */
    void addConsumer(NettyHttpConsumer consumer);

    /**
     * Removes the given consumer
     */
    void removeConsumer(NettyHttpConsumer consumer);

    /**
     * Number of active consumers
     */
    int consumers();

    /**
     * Gets the {@link ChannelHandler}
     */
    ChannelHandler getChannelHandler();

}
