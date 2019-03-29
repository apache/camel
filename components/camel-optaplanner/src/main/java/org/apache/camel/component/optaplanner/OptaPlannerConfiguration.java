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
package org.apache.camel.component.optaplanner;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class OptaPlannerConfiguration {

    @UriPath @Metadata(required = true)
    private String configFile;
    @UriParam(label = "common", defaultValue = "DEFAULT_SOLVER")
    private String solverId = OptaPlannerConstants.DEFAULT_SOLVER_ID;
    @UriParam(label = "producer", defaultValue = "10")
    private int threadPoolSize = 10;
    @UriParam(label = "producer")
    private boolean async;

    public String getConfigFile() {
        return configFile;
    }

    /**
     * Specifies the location to the solver file
     */
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public String getSolverId() {
        return solverId;
    }

    /**
     * Specifies the solverId to user for the solver instance key
     */
    public void setSolverId(String solverId) {
        this.solverId = solverId;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * Specifies the thread pool size to use when async is true
     */
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public boolean isAsync() {
        return async;
    }

    /**
     * Specifies to perform operations in async mode
     */
    public void setAsync(boolean async) {
        this.async = async;
    }
}
