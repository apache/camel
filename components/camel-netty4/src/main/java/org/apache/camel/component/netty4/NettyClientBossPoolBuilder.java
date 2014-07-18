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

import java.util.concurrent.Executors;

import org.jboss.netty.channel.socket.nio.BossPool;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.util.Timer;

/**
 * A builder to create Netty {@link org.jboss.netty.channel.socket.nio.BossPool} which can be used for sharing boss pools
 * with multiple Netty {@link NettyServerBootstrapFactory} server bootstrap configurations.
 */
public final class NettyClientBossPoolBuilder {

    private String name = "NettyClientBoss";
    private String pattern;
    private int bossCount = 1;
    private Timer timer;

    public void setName(String name) {
        this.name = name;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setBossCount(int bossCount) {
        this.bossCount = bossCount;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public NettyClientBossPoolBuilder withName(String name) {
        setName(name);
        return this;
    }

    public NettyClientBossPoolBuilder withPattern(String pattern) {
        setPattern(pattern);
        return this;
    }

    public NettyClientBossPoolBuilder withBossCount(int bossCount) {
        setBossCount(bossCount);
        return this;
    }

    public NettyClientBossPoolBuilder withTimer(Timer timer) {
        setTimer(timer);
        return this;
    }

    /**
     * Creates a new boss pool.
     */
    BossPool build() {
        return new NioClientBossPool(Executors.newCachedThreadPool(), bossCount, timer, new CamelNettyThreadNameDeterminer(pattern, name));
    }
}
