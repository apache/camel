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

    @UriParam(description = "The filter of the monitored items of the consumer. The value dataChangeFilter sets a"
                            + " DataChangeFilter built from the dataChangeFilterTrigger, dataChangeFilterDeadbandType and"
                            + " dataChangeFilterDeadbandValue options. If not set, the monitored items have no filter.")
    private MonitorFilterType monitorFilterType;

    @UriParam(defaultValue = "0",
              description = "The deadband type of the DataChangeFilter: 0 for none, 1 for an absolute deadband, 2 for a"
                            + " percentage of the EU range of the node. Only used when monitorFilterType is"
                            + " dataChangeFilter.")
    private Integer dataChangeFilterDeadbandType = Integer.valueOf(0);

    @UriParam(defaultValue = "0.0",
              description = "The deadband of the DataChangeFilter: a change of the value that is not larger than the"
                            + " deadband is not reported. Its unit depends on dataChangeFilterDeadbandType. Only used when"
                            + " monitorFilterType is dataChangeFilter.")
    private Double dataChangeFilterDeadbandValue = 0.0;

    @UriParam(defaultValue = "StatusValueTimestamp",
              description = "The changes that the DataChangeFilter reports: Status for a change of the status, StatusValue"
                            + " also for a change of the value, and StatusValueTimestamp also for a change of the source"
                            + " timestamp. Only used when monitorFilterType is dataChangeFilter.")
    private DataChangeTrigger dataChangeFilterTrigger = DataChangeTrigger.StatusValueTimestamp;

    public MonitorFilterType getMonitorFilterType() {
        return monitorFilterType;
    }

    public void setMonitorFilterType(MonitorFilterType monitorFilterType) {
        this.monitorFilterType = monitorFilterType;
    }

    public Integer getDataChangeFilterDeadbandType() {
        return dataChangeFilterDeadbandType;
    }

    public void setDataChangeFilterDeadbandType(Integer dataChangeFilterDeadbandType) {
        if (dataChangeFilterDeadbandType == null) {
            throw new IllegalArgumentException("dataChangeFilterDeadbandType must not be null");
        }
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
        if (this.dataChangeFilterDeadbandType == null) {
            throw new IllegalStateException("dataChangeFilterDeadbandType must not be null");
        }

        switch (monitorFilterType) {
            default:
                return new DataChangeFilter(
                        this.getDataChangeFilterTrigger(),
                        UInteger.valueOf(this.getDataChangeFilterDeadbandType()),
                        this.getDataChangeFilterDeadbandValue());
        }
    }
}
