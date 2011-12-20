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
package org.apache.camel;

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
 *
 * @version 
 */
@XmlType
@XmlEnum(String.class)
public enum ThreadPoolRejectedPolicy {

    Abort, CallerRuns, DiscardOldest, Discard;

    public RejectedExecutionHandler asRejectedExecutionHandler() {
        if (this == Abort) {
            return new ThreadPoolExecutor.AbortPolicy();
        } else if (this == CallerRuns) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        } else if (this == DiscardOldest) {
            return new ThreadPoolExecutor.DiscardOldestPolicy();
        } else if (this == Discard) {
            return new ThreadPoolExecutor.DiscardPolicy();
        }
        throw new IllegalArgumentException("Unknown ThreadPoolRejectedPolicy: " + this);
    }

}
