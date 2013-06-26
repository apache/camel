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

import org.apache.camel.Service;
import org.apache.camel.component.netty.NettyServerBootstrapConfiguration;
import org.apache.camel.component.netty.NettyServerBootstrapFactory;
import org.apache.camel.spi.ClassResolver;

/**
 * A single interface to easily configure and setup a shared Netty HTTP server
 * to be re-used among other Camel applications.
 * <p/>
 * To use this, just define a {@link NettyServerBootstrapConfiguration} configuration, and
 * set this using {@link #setNettyServerBootstrapConfiguration(org.apache.camel.component.netty.NettyServerBootstrapConfiguration)}.
 * Then call the {@link #start()} to initialize this shared server.
 */
public interface NettySharedHttpServer extends Service {

    /**
     * Sets the bootstrap configuration to use by this shared Netty HTTP server.
     */
    void setNettyServerBootstrapConfiguration(NettyServerBootstrapConfiguration configuration);

    /**
     * To use a custom {@link ClassResolver} for loading resource on the classpath.
     */
    void setClassResolver(ClassResolver classResolver);

    /**
     * Gets the port number this Netty HTTP server uses.
     */
    int getPort();

    /**
     * Gets the {@link HttpServerConsumerChannelFactory} to use.
     */
    HttpServerConsumerChannelFactory getConsumerChannelFactory();

    /**
     * Gets the {@link NettyServerBootstrapFactory} to use.
     */
    NettyServerBootstrapFactory getServerBootstrapFactory();

}
