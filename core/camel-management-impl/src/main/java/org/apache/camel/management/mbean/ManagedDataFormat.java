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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedDataFormatMBean;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.ManagementStrategy;

@ManagedResource(description = "Managed DataFormat")
public class ManagedDataFormat implements ManagedInstance, ManagedDataFormatMBean {
    private final CamelContext camelContext;
    private final DataFormat dataFormat;

    public ManagedDataFormat(CamelContext camelContext, DataFormat dataFormat) {
        this.camelContext = camelContext;
        this.dataFormat = dataFormat;
    }

    public void init(ManagementStrategy strategy) {
        // noop
    }

    public DataFormat getDataFormat() {
        return dataFormat;
    }

    public CamelContext getContext() {
        return camelContext;
    }

    @Override
    public String getName() {
        if (dataFormat instanceof DataFormatName) {
            return ((DataFormatName) dataFormat).getDataFormatName();
        }
        return null;
    }

    @Override
    public String getCamelId() {
        return camelContext.getName();
    }

    @Override
    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    @Override
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (dataFormat instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) dataFormat).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    @Override
    public DataFormat getInstance() {
        return dataFormat;
    }

}
