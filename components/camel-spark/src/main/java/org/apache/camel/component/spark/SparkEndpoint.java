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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.spark.api.java.JavaRDDLike;
import org.apache.spark.sql.DataFrame;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The spark component can be used to send RDD or DataFrame jobs to Apache Spark cluster.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "spark", title = "Apache Spark", syntax = "spark:endpointType",
        producerOnly = true, label = "bigdata,iot")
public class SparkEndpoint extends DefaultEndpoint {

    // Logger

    private static final Logger LOG = getLogger(SparkEndpoint.class);

    // Endpoint collaborators

    @UriPath @Metadata(required = "true")
    private EndpointType endpointType;
    @UriParam
    private JavaRDDLike rdd;
    @UriParam
    private RddCallback rddCallback;
    @UriParam
    private DataFrame dataFrame;
    @UriParam
    private DataFrameCallback dataFrameCallback;

    // Endpoint configuration

    @UriParam(defaultValue = "true")
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
        LOG.trace("Creating {} Spark producer.", endpointType);
        if (endpointType == EndpointType.rdd) {
            LOG.trace("About to create RDD producer.");
            return new RddSparkProducer(this);
        } else if (endpointType == EndpointType.dataframe) {
            LOG.trace("About to create DataFrame producer.");
            return new DataFrameSparkProducer(this);
        } else {
            LOG.trace("About to create Hive producer.");
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

    public EndpointType getEndpointType() {
        return endpointType;
    }

    /**
     * Type of the endpoint (rdd, dataframe, hive).
     */
    public void setEndpointType(EndpointType endpointType) {
        this.endpointType = endpointType;
    }

    public JavaRDDLike getRdd() {
        return rdd;
    }

    /**
     * RDD to compute against.
     */
    public void setRdd(JavaRDDLike rdd) {
        this.rdd = rdd;
    }

    public RddCallback getRddCallback() {
        return rddCallback;
    }

    /**
     * Function performing action against an RDD.
     */
    public void setRddCallback(RddCallback rddCallback) {
        this.rddCallback = rddCallback;
    }

    public DataFrame getDataFrame() {
        return dataFrame;
    }

    /**
     * DataFrame to compute against.
     */
    public void setDataFrame(DataFrame dataFrame) {
        this.dataFrame = dataFrame;
    }

    public DataFrameCallback getDataFrameCallback() {
        return dataFrameCallback;
    }

    /**
     * Function performing action against an DataFrame.
     */
    public void setDataFrameCallback(DataFrameCallback dataFrameCallback) {
        this.dataFrameCallback = dataFrameCallback;
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