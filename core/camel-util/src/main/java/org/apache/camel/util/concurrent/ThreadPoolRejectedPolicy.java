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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/**
 * Represent the kinds of options for rejection handlers for thread pools.
 * <p/>
 * These options are used for fine grained thread pool settings, where you
 * want to control which handler to use when a thread pool cannot execute
 * a new task.
 * <p/>
 * Camel will by default use <tt>CallerRuns</tt>.
 */
@XmlType
@XmlEnum
public enum ThreadPoolRejectedPolicy {

    Abort, CallerRuns, DiscardOldest, Discard;

    public RejectedExecutionHandler asRejectedExecutionHandler() {
        if (this == Abort) {
            return new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    if (r instanceof Rejectable) {
                        ((Rejectable)r).reject();
                    } else {
                        throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + executor.toString());
                    }
                }

                @Override
                public String toString() {
                    return "Abort";
                }
            };
        } else if (this == CallerRuns) {
            return new ThreadPoolExecutor.CallerRunsPolicy() {
                @Override
                public String toString() {
                    return "CallerRuns";
                }
            };
        } else if (this == DiscardOldest) {
            return new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    if (!executor.isShutdown()) {
                        Runnable rejected = executor.getQueue().poll();
                        if (rejected instanceof Rejectable) {
                            ((Rejectable) rejected).reject();
                        }
                        executor.execute(r);
                    }
                }

                @Override
                public String toString() {
                    return "DiscardOldest";
                }
            };
        } else if (this == Discard) {
            return new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    if (r instanceof Rejectable) {
                        ((Rejectable) r).reject();
                    }
                }

                @Override
                public String toString() {
                    return "Discard";
                }
            };
        }
        throw new IllegalArgumentException("Unknown ThreadPoolRejectedPolicy: " + this);
    }

}
