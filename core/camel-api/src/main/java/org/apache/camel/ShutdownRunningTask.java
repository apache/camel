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
package org.apache.camel;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Per-consumer policy controlling how many pending tasks are processed before the consumer stops during
 * <a href="https://camel.apache.org/manual/graceful-shutdown.html">graceful shutdown</a>.
 * <p/>
 * Set on a route via the DSL {@code .shutdownRunningTask(ShutdownRunningTask.CompleteAllTasks)} call. Most consumers
 * handle a single task at a time, so the distinction only matters for {@link BatchConsumer} implementations (such as
 * file or JPA consumers) that can have multiple pending messages in a batch when shutdown is triggered.
 * <ul>
 * <li>{@link #CompleteCurrentTaskOnly} - the <b>default</b>: finish the current in-flight task and then stop; any
 * remaining pending tasks are left unprocessed.</li>
 * <li>{@link #CompleteAllTasks} - drain the entire current batch before stopping; only applicable to
 * {@link BatchConsumer} consumers.</li>
 * </ul>
 *
 * @see ShutdownRoute
 * @see BatchConsumer
 * @see org.apache.camel.spi.ShutdownStrategy
 */
@XmlType
@XmlEnum
public enum ShutdownRunningTask {

    CompleteCurrentTaskOnly,
    CompleteAllTasks

}
