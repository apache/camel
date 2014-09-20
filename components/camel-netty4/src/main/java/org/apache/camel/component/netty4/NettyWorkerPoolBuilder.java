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
package org.apache.camel.component.netty4;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.apache.camel.util.concurrent.CamelThreadFactory;

/**
 * A builder to create Netty {@link io.netty.channel.EventLoopGroup} which can be used for sharing worker pools
 * with multiple Netty {@link NettyServerBootstrapFactory} server bootstrap configurations.
 */
public final class NettyWorkerPoolBuilder {

    private String name = "NettyWorker";
    private String pattern;
    private int workerCount;
    private volatile EventLoopGroup workerPool;

    public void setName(String name) {
        this.name = name;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public NettyWorkerPoolBuilder withName(String name) {
        setName(name);
        return this;
    }

    public NettyWorkerPoolBuilder withPattern(String pattern) {
        setPattern(pattern);
        return this;
    }

    public NettyWorkerPoolBuilder withWorkerCount(int workerCount) {
        setWorkerCount(workerCount);
        return this;
    }

    /**
     * Creates a new worker pool.
     */
    public EventLoopGroup build() {
        int count = workerCount > 0 ? workerCount : NettyHelper.DEFAULT_IO_THREADS;
        workerPool = new NioEventLoopGroup(count, new CamelThreadFactory(pattern, name, false));
        return workerPool;
    }

    /**
     * Shutdown the created worker pool
     */
    public void destroy() {
        if (workerPool != null) {
            workerPool.shutdownGracefully();
            workerPool = null;
        }
    }
}
