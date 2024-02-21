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
package org.apache.camel.cli.connector;

import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.annotations.JdkService;

@JdkService(CliConnectorFactory.FACTORY)
public class DefaultCliConnectorFactory implements CliConnectorFactory {

    private boolean enabled = true;
    private String runtime;
    private String runtimeVersion;
    private String runtimeStartClass;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getRuntime() {
        return runtime;
    }

    @Override
    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    @Override
    public String getRuntimeStartClass() {
        return runtimeStartClass;
    }

    @Override
    public void setRuntimeStartClass(String runtimeStartClass) {
        this.runtimeStartClass = runtimeStartClass;
    }

    @Override
    public CliConnector createConnector() {
        if (enabled) {
            return new LocalCliConnector(this);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "camel-cli-connector";
    }
}
