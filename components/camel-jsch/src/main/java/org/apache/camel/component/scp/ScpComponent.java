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
package org.apache.camel.component.scp;

import java.net.URI;
import java.util.Map;

import com.jcraft.jsch.JSch;
import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component providing secure messaging using JSch
 */
public class ScpComponent extends RemoteFileComponent<ScpFile> {

    private static final Logger LOG = LoggerFactory.getLogger(ScpComponent.class);

    private boolean verboseLogging;

    public ScpComponent() {
    }

    public ScpComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected GenericFileEndpoint<ScpFile> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        int query = uri.indexOf("?");
        return new ScpEndpoint(uri, this, new ScpConfiguration(new URI(query >= 0 ? uri.substring(0, query) : uri)));
    }

    protected void afterPropertiesSet(GenericFileEndpoint<ScpFile> endpoint) throws Exception {
        // noop
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        initJsch();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // noop
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    /**
     * JSCH is verbose logging out of the box. Therefore we turn the logging down to DEBUG logging by default.
     * But setting this option to <tt>true</tt> turns on the verbose logging again.
     */
    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    protected void initJsch()  {
        JSch.setConfig("StrictHostKeyChecking",  "yes");
        JSch.setLogger(new com.jcraft.jsch.Logger() {
            @Override
            public boolean isEnabled(int level) {
                return level == FATAL || level == ERROR ? LOG.isErrorEnabled()
                        : level == WARN ? LOG.isWarnEnabled()
                        : level == INFO ? LOG.isInfoEnabled() : LOG.isDebugEnabled();
            }

            @Override
            public void log(int level, String message) {
                if (level == FATAL || level == ERROR) {
                    LOG.error("[JSCH] {}", message);
                } else if (level == WARN) {
                    LOG.warn("[JSCH] {}", message);
                } else if (level == INFO) {
                    // JSCH is verbose at INFO logging so allow to turn the noise down and log at DEBUG by default
                    if (isVerboseLogging()) {
                        LOG.info("[JSCH] {}", message);
                    } else {
                        LOG.debug("[JSCH] {}", message);
                    }
                } else {
                    LOG.debug("[JSCH] {}", message);
                }
            }
        });
    }

}

