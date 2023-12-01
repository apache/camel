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
 * Defines the existing type of threads. The virtual threads can only be used with JDK 21+ and the system property
 * {@code camel.threads.virtual.enabled} set to {@code true}.
 * The default value is {@code false} which means that platform threads are used by default.
 */
public enum ThreadType {
    PLATFORM,
    VIRTUAL;
    private static final Logger LOG = LoggerFactory.getLogger(ThreadType.class);
    private static final ThreadType CURRENT = Boolean.getBoolean("camel.threads.virtual.enabled") ? VIRTUAL : PLATFORM;
    static {
        LOG.info("The type of thread detected is {}", CURRENT);
    }
    public static ThreadType current() {
        return CURRENT;
    }
}
