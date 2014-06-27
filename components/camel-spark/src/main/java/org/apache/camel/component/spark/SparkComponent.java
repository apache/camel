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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import spark.Spark;
import spark.SparkBase;

public class SparkComponent extends UriEndpointComponent {

    private int port = SparkBase.SPARK_DEFAULT_PORT;
    private SparkConfiguration sparkConfiguration = new SparkConfiguration();
    private SparkBinding sparkBinding = new DefaultSparkBinding();

    public SparkComponent() {
        super(SparkEndpoint.class);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public SparkConfiguration getSparkConfiguration() {
        return sparkConfiguration;
    }

    public void setSparkConfiguration(SparkConfiguration sparkConfiguration) {
        this.sparkConfiguration = sparkConfiguration;
    }

    public SparkBinding getSparkBinding() {
        return sparkBinding;
    }

    public void setSparkBinding(SparkBinding sparkBinding) {
        this.sparkBinding = sparkBinding;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SparkEndpoint answer = new SparkEndpoint(uri, this);
        answer.setSparkConfiguration(getSparkConfiguration());
        answer.setSparkBinding(getSparkBinding());
        setProperties(answer, parameters);

        if (!remaining.contains(":")) {
            throw new IllegalArgumentException("Invalid syntax. Must be spark:verb:path");
        }

        String verb = ObjectHelper.before(remaining, ":");
        String path = ObjectHelper.after(remaining, ":");

        answer.setVerb(verb);
        answer.setPath(path);

        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Spark.setPort(getPort());
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        Spark.stop();
    }
}
