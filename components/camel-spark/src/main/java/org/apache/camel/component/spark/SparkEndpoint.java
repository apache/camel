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
package org.apache.camel.component.spark;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.spark.api.java.AbstractJavaRDDLike;
import org.apache.spark.sql.DataFrame;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

// @UriEndpoint(scheme = "spark", producerOnly = true, title = "Apache Spark", syntax = "spark:jobType", label = "bigdata,iot")
public class SparkEndpoint extends DefaultEndpoint {

    // Logger

    private static final Logger LOG = getLogger(SparkEndpoint.class);

    // Endpoint collaborators

    @UriParam(name = "rdd", description = "RDD to compute against.")
    private AbstractJavaRDDLike rdd;

    @UriParam(name = "rddCallback", description = "Function performing action against an RDD.")
    private RddCallback rddCallback;

    @UriParam(name = "dataFrame", description = "DataFrame to compute against.")
    private DataFrame dataFrame;

    @UriParam(name = "dataFrameCallback", description = "Function performing action against an DataFrame.")
    private DataFrameCallback dataFrameCallback;

    // Endpoint configuration

    @UriParam(name = "endpointType", description = "Type of the endpoint (rdd, dataframe, hive).")
    private final EndpointType endpointType;

    @UriParam(name = "collect", description = "Indicates if results should be collected or counted.")
    private boolean collect = true;

    // Constructors

    public SparkEndpoint(String endpointUri, SparkComponent component, EndpointType endpointType) {
        super(endpointUri, component);
        this.endpointType = endpointType;
    }

    // Life-cycle

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (rdd == null) {
            rdd = getComponent().getRdd();
        }
        if (rddCallback == null) {
            rddCallback = getComponent().getRddCallback();
        }
    }

    // Overridden

    @Override
    public Producer createProducer() throws Exception {
        LOG.debug("Creating {} Spark producer.", endpointType);
        if (endpointType == EndpointType.rdd) {
            return new RddSparkProducer(this);
        } else if (endpointType == EndpointType.dataframe) {
            return new DataFrameSparkProducer(this);
        } else {
            return new HiveSparkProducer(this);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Spark component supports producer endpoints only.");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    // Setters & getters


    @Override
    public SparkComponent getComponent() {
        return (SparkComponent) super.getComponent();
    }

    public AbstractJavaRDDLike getRdd() {
        return rdd;
    }

    public void setRdd(AbstractJavaRDDLike rdd) {
        this.rdd = rdd;
    }

    public RddCallback getRddCallback() {
        return rddCallback;
    }

    public void setRddCallback(RddCallback rddCallback) {
        this.rddCallback = rddCallback;
    }

    public DataFrame getDataFrame() {
        return dataFrame;
    }

    public void setDataFrame(DataFrame dataFrame) {
        this.dataFrame = dataFrame;
    }

    public DataFrameCallback getDataFrameCallback() {
        return dataFrameCallback;
    }

    public void setDataFrameCallback(DataFrameCallback dataFrameCallback) {
        this.dataFrameCallback = dataFrameCallback;
    }

    public boolean isCollect() {
        return collect;
    }

    public void setCollect(boolean collect) {
        this.collect = collect;
    }

}