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
package org.apache.camel.component.flink;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.streaming.api.datastream.DataStream;

/**
 * The flink component can be used to send DataSet or DataStream jobs to Apache Flink cluster.
 */
@Component("flink")
public class FlinkComponent extends DefaultComponent {

    private DataSet ds;
    private DataSetCallback dataSetCallback;
    private DataStream dataStream;
    private DataStreamCallback dataStreamCallback;

    public FlinkComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EndpointType type = getCamelContext().getTypeConverter().mandatoryConvertTo(EndpointType.class, remaining);
        return new FlinkEndpoint(uri, this, type);
    }

    public DataSet getDataSet() {
        return ds;
    }

    /**
     * DataSet to compute against.
     */
    public void setDataSet(DataSet ds) {
        this.ds = ds;
    }

    public DataStream getDataStream() {
        return dataStream;
    }

    /**
     * DataStream to compute against.
     */
    public void setDataStream(DataStream dataStream) {
        this.dataStream = dataStream;
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

    public DataStreamCallback getDataStreamCallback() {
        return dataStreamCallback;
    }

    /**
     * Function performing action against a DataStream.
     */
    public void setDataStreamCallback(DataStreamCallback dataStreamCallback) {
        this.dataStreamCallback = dataStreamCallback;
    }
}