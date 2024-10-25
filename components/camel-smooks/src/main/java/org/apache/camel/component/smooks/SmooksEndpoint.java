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
package org.apache.camel.component.smooks;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ProcessorEndpoint;
import org.apache.camel.support.service.ServiceHelper;

/**
 * EDI, XML, CSV, etc. based data transformation using Smooks.
 */
@UriEndpoint(firstVersion = "4.7.0", scheme = "smooks", title = "Smooks", syntax = "smooks:smooksConfig",
             category = { Category.TRANSFORMATION })
public class SmooksEndpoint extends ProcessorEndpoint {

    @UriPath(description = "Smooks XML configuration file")
    @Metadata(required = true, supportFileReference = true)
    private String smooksConfig;

    @UriPath(description = "File path to place the generated HTML execution report. Do not set in production since this is a major performance drain")
    private String reportPath;

    private final SmooksProcessor smooksProcessor;

    public SmooksEndpoint(String endpointUri, Component component, SmooksProcessor processor) {
        super(endpointUri, component, processor);
        this.smooksProcessor = processor;
    }

    public String getSmooksConfig() {
        return smooksConfig;
    }

    public void setSmooksConfig(String smooksConfig) {
        this.smooksConfig = smooksConfig;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(smooksProcessor);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(smooksProcessor);
    }

}
