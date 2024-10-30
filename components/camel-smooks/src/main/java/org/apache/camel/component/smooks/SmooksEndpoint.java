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
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Use Smooks to transform, route, and bind both XML and non-XML data, including EDI, CSV, JSON, and YAML.
 */
@UriEndpoint(firstVersion = "4.7.0", scheme = "smooks", title = "Smooks", syntax = "smooks:smooksConfig",
             producerOnly = true,
             category = { Category.TRANSFORMATION })
public class SmooksEndpoint extends DefaultEndpoint {

    @UriPath(description = "Path to the Smooks configuration file")
    @Metadata(required = true, supportFileReference = true)
    private String smooksConfig;
    @UriParam(description = "File path to place the generated HTML execution report. The report is a useful tool in the developer’s arsenal for diagnosing issues or comprehending a transformation. Do not set in production since this is a major performance drain")
    private String reportPath;

    private final SmooksProcessor smooksProcessor;

    public SmooksEndpoint(String endpointUri, Component component, SmooksProcessor processor) {
        super(endpointUri, component);
        this.smooksProcessor = processor;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SmooksProducer(this, smooksProcessor);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new IllegalArgumentException("Consumer is not supported");
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

    public String getSmooksConfig() {
        return smooksConfig;
    }

    public void setSmooksConfig(String smooksConfig) {
        this.smooksConfig = smooksConfig;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }
}
