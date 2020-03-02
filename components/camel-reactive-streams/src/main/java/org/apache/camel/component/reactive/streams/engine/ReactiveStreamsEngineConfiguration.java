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
package org.apache.camel.component.reactive.streams.engine;

/**
 * Configuration parameters for the Camel internal reactive-streams engine.
 */
public class ReactiveStreamsEngineConfiguration {

    private String threadPoolName = "CamelReactiveStreamsWorker";
    private int threadPoolMinSize;
    private int threadPoolMaxSize = 10;

    public ReactiveStreamsEngineConfiguration() {
    }

    public String getThreadPoolName() {
        return threadPoolName;
    }

    /**
     * The name of the thread pool used by the reactive streams internal engine.
     */
    public void setThreadPoolName(String threadPoolName) {
        this.threadPoolName = threadPoolName;
    }

    public int getThreadPoolMinSize() {
        return threadPoolMinSize;
    }

    /**
     * The minimum number of threads used by the reactive streams internal engine.
     */
    public void setThreadPoolMinSize(int threadPoolMinSize) {
        this.threadPoolMinSize = threadPoolMinSize;
    }

    public int getThreadPoolMaxSize() {
        return threadPoolMaxSize;
    }

    /**
     * The maximum number of threads used by the reactive streams internal engine.
     */
    public void setThreadPoolMaxSize(int threadPoolMaxSize) {
        this.threadPoolMaxSize = threadPoolMaxSize;
    }
}
