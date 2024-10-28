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
package org.apache.camel.component.torchserve.client.model;

public class UnregisterOptions {

    /**
     * Decides whether the call is synchronous or not, default: false. (optional, default to false)
     */
    private Boolean synchronous = null;
    /**
     * Waiting up to the specified wait time if necessary for a worker to complete all pending requests. Use 0 to
     * terminate backend worker process immediately. Use -1 for wait infinitely. (optional)
     */
    private Integer timeout = null;

    private UnregisterOptions() {
    }

    public static UnregisterOptions empty() {
        return new UnregisterOptions();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Boolean getSynchronous() {
        return synchronous;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public static class Builder {

        private final UnregisterOptions options = new UnregisterOptions();

        public Builder synchronous(Boolean synchronous) {
            options.synchronous = synchronous;
            return this;
        }

        public Builder timeout(Integer timeout) {
            options.timeout = timeout;
            return this;
        }

        public UnregisterOptions build() {
            return options;
        }
    }
}
