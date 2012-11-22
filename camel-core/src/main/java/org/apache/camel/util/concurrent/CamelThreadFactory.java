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
package org.apache.camel.util.concurrent;

import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread factory which creates threads supporting a naming pattern.
 */
public final class CamelThreadFactory implements ThreadFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CamelThreadFactory.class);

    private final String pattern;
    private final String name;
    private final boolean daemon;

    public CamelThreadFactory(String pattern, String name, boolean daemon) {
        this.pattern = pattern;
        this.name = name;
        this.daemon = daemon;
    }

    public Thread newThread(Runnable runnable) {
        String threadName = ThreadHelper.resolveThreadName(pattern, name);
        Thread answer = new Thread(runnable, threadName);
        answer.setDaemon(daemon);

        LOG.trace("Created thread[{}] -> {}", threadName, answer);
        return answer;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return "CamelThreadFactory[" + name + "]";
    }
}