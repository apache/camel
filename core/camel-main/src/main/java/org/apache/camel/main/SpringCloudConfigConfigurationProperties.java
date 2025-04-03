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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.vault.SpringCloudConfigConfiguration;

@Configurer(extended = true)
public class SpringCloudConfigConfigurationProperties extends SpringCloudConfigConfiguration implements BootstrapCloseable {
    private MainConfigurationProperties parent;

    public SpringCloudConfigConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    // getter and setters
    // --------------------------------------------------------------

    // these are inherited from the parent class

    // fluent builders
    // --------------------------------------------------------------

    public SpringCloudConfigConfigurationProperties withUser(String user) {
        setUsername(user);
        return this;
    }

    public SpringCloudConfigConfigurationProperties withPassword(String password) {
        setPassword(password);
        return this;
    }

    public SpringCloudConfigConfigurationProperties withUris(String uris) {
        setUris(uris);
        return this;
    }

    public SpringCloudConfigConfigurationProperties withRefreshEnabled(boolean refreshEanbled) {
        setRefreshEnabled(refreshEanbled);
        return this;
    }

    public SpringCloudConfigConfigurationProperties withRefreshPeriod(int refreshPeriod) {
        setRefreshPeriod(refreshPeriod);
        return this;
    }
}
