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

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.flink.api.java.DataSet;

import java.util.Map;

/**
 * The flink component can be used to send DataSet or DataStream jobs to Apache Flink cluster.
 */
public class FlinkComponent extends UriEndpointComponent {

    private DataSet ds;
    private DataSetCallback dataSetCallback;

    public FlinkComponent() {
        super(FlinkEndpoint.class);
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

    public DataSetCallback getDataSetCallback() {
        return dataSetCallback;
    }

    /**
     * Function performing action against a DataSet.
     */
    public void setDataSetCallback(DataSetCallback dataSetCallback) {
        this.dataSetCallback = dataSetCallback;
    }
}