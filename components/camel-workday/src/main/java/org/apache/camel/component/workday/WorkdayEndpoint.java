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
package org.apache.camel.component.workday;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Represents a Workday endpoint.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "workday", title = "Workday", syntax = "workday:entity:path", producerOnly = true, label = "hcm")
public class WorkdayEndpoint extends DefaultEndpoint {

    @UriParam
    private WorkdayConfiguration workdayConfiguration;

    public WorkdayEndpoint() {
    }

    public WorkdayEndpoint(String uri, WorkdayComponent component, WorkdayConfiguration configuration) {
        super(uri, component);
        this.workdayConfiguration = configuration;
    }

    public Producer createProducer() throws Exception {
        switch (workdayConfiguration.getEntity()) {
            case report:
                return new WorkdayReportProducer(this);
            default:
                throw new UnsupportedOperationException(String.format("Workday producer %s is not implemented", workdayConfiguration.getEntity()));
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Workday consumer is not implemented.");
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    public WorkdayConfiguration getWorkdayConfiguration() {
        return workdayConfiguration;
    }

    public void setWorkdayConfiguration(WorkdayConfiguration workdayConfiguration) {
        this.workdayConfiguration = workdayConfiguration;
    }
}
