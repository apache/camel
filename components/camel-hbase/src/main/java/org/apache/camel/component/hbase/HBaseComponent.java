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
package org.apache.camel.component.hbase;

import java.util.Map;
import java.util.concurrent.Executors;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.PropertiesHelper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;

@Component("hbase")
public class HBaseComponent extends DefaultComponent {

    private Connection connection;

    @Metadata(label = "advanced")
    private Configuration configuration;
    @Metadata(defaultValue = "10")
    private int poolMaxSize = 10;

    public HBaseComponent() {
    }

    public HBaseComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();

            ClassLoader applicationContextClassLoader = getCamelContext().getApplicationContextClassLoader();
            if (applicationContextClassLoader != null) {
                configuration.setClassLoader(applicationContextClassLoader);
                HBaseConfiguration.addHbaseResources(configuration);
            }
        }

        connection = ConnectionFactory.createConnection(configuration, Executors.newFixedThreadPool(poolMaxSize));
    }

    @Override
    protected void doStop() throws Exception {
        if (connection != null) {
            // this will also shutdown the thread pool
            connection.close();
        }
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        HBaseEndpoint endpoint = new HBaseEndpoint(uri, this, remaining);
        Map<String, Object> mapping = PropertiesHelper.extractProperties(parameters, "row.");
        endpoint.setRowMapping(mapping);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public Connection getConnection() {
        return connection;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared configuration
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public int getPoolMaxSize() {
        return poolMaxSize;
    }

    /**
     * Maximum number of references to keep for each table in the HTable pool.
     * The default value is 10.
     */
    public void setPoolMaxSize(int poolMaxSize) {
        this.poolMaxSize = poolMaxSize;
    }
}
