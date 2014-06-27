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
import spark.Spark;
import spark.SparkBase;

public class SparkComponent extends UriEndpointComponent {

    private int port = SparkBase.SPARK_DEFAULT_PORT;

    public SparkComponent() {
        super(SparkEndpoint.class);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SparkEndpoint answer = new SparkEndpoint(uri, this);
        setProperties(answer, parameters);

        String[] parts = remaining.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid syntax. Must be spark:verb:path");
        }

        answer.setVerb(parts[0]);
        answer.setPath(parts[1]);

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
