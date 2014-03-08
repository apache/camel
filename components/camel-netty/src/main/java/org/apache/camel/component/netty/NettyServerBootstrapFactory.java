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
package org.apache.camel.component.netty;

import java.util.concurrent.ThreadFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.SuspendableService;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;

/**
 * Factory for setting up Netty {@link org.jboss.netty.bootstrap.ServerBootstrap} and all
 * the needed logic for doing that.
 * <p/>
 * This factory allows for consumers to reuse existing {@link org.jboss.netty.bootstrap.ServerBootstrap} which
 * allows to share the same port for multiple consumers.
 */
public interface NettyServerBootstrapFactory extends SuspendableService {

    /**
     * Initializes this <b>non-shared</b> {@link NettyServerBootstrapFactory}.
     *
     * @param camelContext     the {@link CamelContext} for non-shared bootstrap factory
     * @param configuration    the bootstrap configuration
     * @param pipelineFactory  the pipeline factory
     */
    void init(CamelContext camelContext, NettyServerBootstrapConfiguration configuration, ChannelPipelineFactory pipelineFactory);

    /**
     * Initializes this <b>shared</b> {@link NettyServerBootstrapFactory}.
     *
     * @param threadFactory    the thread factory to use for shared bootstrap factory
     * @param configuration    the bootstrap configuration
     * @param pipelineFactory  the pipeline factory
     */
    void init(ThreadFactory threadFactory, NettyServerBootstrapConfiguration configuration, ChannelPipelineFactory pipelineFactory);

    /**
     * When a new {@link Channel} is opened.
     */
    void addChannel(Channel channel);

    /**
     * When a {@link Channel} is closed.
     */
    void removeChannel(Channel channel);

    /**
     * When a {@link NettyConsumer} is added and uses this bootstrap factory.
     */
    void addConsumer(NettyConsumer consumer);

    /**
     * When a {@link NettyConsumer} is removed and no longer using this bootstrap factory.
     */
    void removeConsumer(NettyConsumer consumer);

}
