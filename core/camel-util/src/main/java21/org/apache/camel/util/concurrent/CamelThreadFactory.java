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
package org.apache.camel.util.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread factory which creates threads supporting a naming pattern.
 * The factory creates virtual threads in case the System property {@code camel.threads.virtual.enabled} set to
 * {@code true}.
 */
public final class CamelThreadFactory implements ThreadFactoryTypeAware {
    private static final Logger LOG = LoggerFactory.getLogger(CamelThreadFactory.class);

    private static final ThreadFactoryType TYPE = ThreadFactoryType.current();

    private final String pattern;
    private final String name;
    private final boolean daemon;
    private final ThreadFactoryType threadType;

    public CamelThreadFactory(String pattern, String name, boolean daemon) {
        this.pattern = pattern;
        this.name = name;
        this.daemon = daemon;
        this.threadType = daemon ? TYPE : ThreadFactoryType.PLATFORM;
    }

    @Override
    public boolean isVirtual() {
        return threadType == ThreadFactoryType.VIRTUAL;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        String threadName = ThreadHelper.resolveThreadName(pattern, name);

        Thread answer = threadType.newThread(threadName, daemon, runnable);

        LOG.trace("Created thread[{}] -> {}", threadName, answer);
        return answer;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "CamelThreadFactory[" + name + "]";
    }

    private enum ThreadFactoryType {
        PLATFORM {
            Thread.Builder newThreadBuilder(String threadName, boolean daemon) {
                return Thread.ofPlatform().name(threadName).daemon(daemon);
            }
        },
        VIRTUAL {
            Thread.Builder newThreadBuilder(String threadName, boolean daemon) {
                return Thread.ofVirtual().name(threadName);
            }
        };

        Thread newThread(String threadName, boolean daemon, Runnable runnable) {
            return newThreadBuilder(threadName, daemon).unstarted(runnable);
        }

        abstract Thread.Builder newThreadBuilder(String threadName, boolean daemon);

        static ThreadFactoryType current() {
            return ThreadType.current() == ThreadType.VIRTUAL ? VIRTUAL : PLATFORM;
        }
    }
}

