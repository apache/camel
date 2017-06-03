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

import java.util.concurrent.Executors;

import org.jboss.netty.channel.socket.nio.BossPool;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;


/**
 * A builder to create Netty {@link org.jboss.netty.channel.socket.nio.BossPool} which can be used for sharing boss pools
 * with multiple Netty {@link org.apache.camel.component.netty.NettyServerBootstrapFactory} server bootstrap configurations.
 */
public final class NettyServerBossPoolBuilder {

    private String name = "NettyServerBoss";
    private String pattern;
    private int bossCount = 1;

    public void setName(String name) {
        this.name = name;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setBossCount(int bossCount) {
        this.bossCount = bossCount;
    }

    public NettyServerBossPoolBuilder withName(String name) {
        setName(name);
        return this;
    }

    public NettyServerBossPoolBuilder withPattern(String pattern) {
        setPattern(pattern);
        return this;
    }

    public NettyServerBossPoolBuilder withBossCount(int bossCount) {
        setBossCount(bossCount);
        return this;
    }

    /**
     * Creates a new boss pool.
     */
    public BossPool build() {
        return new NioServerBossPool(Executors.newCachedThreadPool(), bossCount, new CamelNettyThreadNameDeterminer(pattern, name));
    }
}
