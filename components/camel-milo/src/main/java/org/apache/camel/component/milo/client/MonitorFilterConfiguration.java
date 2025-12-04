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

package org.apache.camel.component.milo.client;

import java.util.Objects;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DataChangeTrigger;
import org.eclipse.milo.opcua.stack.core.types.structured.DataChangeFilter;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringFilter;

@UriParams
public class MonitorFilterConfiguration implements Cloneable {

    @UriParam(description = "Monitor Filter Type for MonitoredItems.")
    private MonitorFilterType monitorFilterType;

    @UriParam(defaultValue = "0", description = "Deadband type for MonitorFilterType DataChangeFilter.")
    private UInteger dataChangeFilterDeadbandType = UInteger.valueOf(0);

    @UriParam(defaultValue = "0.0", description = "Deadband value for MonitorFilterType DataChangeFilter.")
    private Double dataChangeFilterDeadbandValue = 0.0;

    @UriParam(
            defaultValue = "StatusValueTimestamp",
            description = "Data change trigger for data change monitor filter type.")
    private DataChangeTrigger dataChangeFilterTrigger = DataChangeTrigger.StatusValueTimestamp;

    public MonitorFilterType getMonitorFilterType() {
        return monitorFilterType;
    }

    public void setMonitorFilterType(MonitorFilterType monitorFilterType) {
        this.monitorFilterType = monitorFilterType;
    }

    public UInteger getDataChangeFilterDeadbandType() {
        return dataChangeFilterDeadbandType;
    }

    public void setDataChangeFilterDeadbandType(UInteger dataChangeFilterDeadbandType) {
        this.dataChangeFilterDeadbandType = dataChangeFilterDeadbandType;
    }

    public Double getDataChangeFilterDeadbandValue() {
        return dataChangeFilterDeadbandValue;
    }

    public void setDataChangeFilterDeadbandValue(Double dataChangeFilterDeadbandValue) {
        this.dataChangeFilterDeadbandValue = dataChangeFilterDeadbandValue;
    }

    public DataChangeTrigger getDataChangeFilterTrigger() {
        return dataChangeFilterTrigger;
    }

    public void setDataChangeFilterTrigger(DataChangeTrigger dataChangeFilterTrigger) {
        this.dataChangeFilterTrigger = dataChangeFilterTrigger;
    }

    public MonitoringFilter createMonitoringFilter() {
        if (Objects.isNull(this.monitorFilterType)) {
            throw new IllegalStateException("No filter type configured.");
        }

        switch (monitorFilterType) {
            default:
                return new DataChangeFilter(
                        this.getDataChangeFilterTrigger(),
                        this.getDataChangeFilterDeadbandType(),
                        this.getDataChangeFilterDeadbandValue());
        }
    }
}
