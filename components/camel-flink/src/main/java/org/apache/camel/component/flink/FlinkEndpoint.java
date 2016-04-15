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

package org.apache.camel.component.flink;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.flink.api.java.DataSet;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The flink component can be used to send DataSet jobs to Apache Flink cluster.
 */
@UriEndpoint(scheme = "META-INF/services/org/apache/camel/component/flink", title = "Apache Flink", syntax = "flink:endpointType",
        producerOnly = true, label = "flink engine, hadoop")
public class FlinkEndpoint extends DefaultEndpoint {

    private static final Logger LOG = getLogger(FlinkEndpoint.class);

    @UriPath @Metadata(required = "true")
    private EndpointType endpointType;

    // DataSet to compute against.
    @UriParam
    private DataSet dataSet;

    @UriParam
    private DataSetCallback dataSetCallback;

    @UriParam(defaultValue = "true")
    private boolean collect = true;

    public FlinkEndpoint(String endpointUri, FlinkComponent component, EndpointType endpointType) {
        super(endpointUri, component);
        this.endpointType = endpointType;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (dataSet == null) {
            dataSet = getComponent().getDataSet();
        }

        if (dataSetCallback == null) {
            dataSetCallback = getComponent().getDataSetCallback();
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        LOG.trace("Creating {} Flink Producer.", endpointType);
        if (endpointType == EndpointType.dataset) {
            LOG.trace("About to create Dataset Producer.");
            return new DataSetFlinkProducer(this);
        }
        else
            return null;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Flink Component supports producer endpoints only.");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public FlinkComponent getComponent() {
        return (FlinkComponent) super.getComponent();
    }

    /**
     * Type of the endpoint (dataset, datastream).
     */
    public void setEndpointType(EndpointType endpointType) {
        this.endpointType = endpointType;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    /**
     * DataSet to compute against.
     */
    public void setDataSet(DataSet ds) {
        this.dataSet = ds;
    }

    public DataSetCallback getDataSetCallback() {
        return dataSetCallback;
    }

    /**
     * Function performing action against a DataSet.
     */
    public void setDataSetCallback(DataSetCallback dataSetCallback) {
        this.dataSetCallback = dataSetCallback;
    }

    public boolean isCollect() {
        return collect;
    }

    /**
     * Indicates if results should be collected or counted.
     */
    public void setCollect(boolean collect) {
        this.collect = collect;
    }
}