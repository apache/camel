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
package org.apache.camel.component.consul.cluster;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.consul.ConsulClientConfiguration;

public class ConsulClusterConfiguration extends ConsulClientConfiguration {
    private int sessionTtl = 60;
    private int sessionLockDelay = 5;
    private int sessionRefreshInterval = 5;
    private String rootPath = "/camel";

    // ***********************************************
    // Properties
    // ***********************************************

    public int getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(int sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public int getSessionLockDelay() {
        return sessionLockDelay;
    }

    public void setSessionLockDelay(int sessionLockDelay) {
        this.sessionLockDelay = sessionLockDelay;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public int getSessionRefreshInterval() {
        return sessionRefreshInterval;
    }

    public void setSessionRefreshInterval(int sessionRefreshInterval) {
        this.sessionRefreshInterval = sessionRefreshInterval;
    }

    // ***********************************************
    //
    // ***********************************************

    @Override
    public ConsulClusterConfiguration copy() {
        try {
            return (ConsulClusterConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
