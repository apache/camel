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
package org.apache.camel.itest.springboot.arquillian.container;

import java.util.logging.Level;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

public class ManagedSEContainerConfiguration implements ContainerConfiguration {

    private boolean debug;
    private String host = "127.0.0.1";
    private int port = 9999;
    private String librariesPath;
    private Level logLevel = Level.INFO;
    private boolean keepDeploymentArchives;
    private String additionalJavaOpts;
    private int waitTime = 5;

    public void validate() throws ConfigurationException {
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getLibrariesPath() {
        return librariesPath;
    }

    public void setLibrariesPath(String librariesPath) {
        this.librariesPath = librariesPath;
    }

    public Level getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = Level.parse(logLevel);
    }

    public boolean isKeepDeploymentArchives() {
        return keepDeploymentArchives;
    }

    public void setKeepDeploymentArchives(boolean keepDeploymentArchives) {
        this.keepDeploymentArchives = keepDeploymentArchives;
    }

    public String getAdditionalJavaOpts() {
        return additionalJavaOpts;
    }

    public void setAdditionalJavaOpts(String additionalJavaOpts) {
        this.additionalJavaOpts = additionalJavaOpts;
    }

}
