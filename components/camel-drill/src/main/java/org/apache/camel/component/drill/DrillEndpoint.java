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
package org.apache.camel.component.drill;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.drill.util.StringUtils;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultPollingEndpoint;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;

/**
 * Perform queries against an Apache Drill cluster.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "drill", title = "Drill", syntax = "drill:host", producerOnly = true,
             category = { Category.DATABASE, Category.BIGDATA }, headersClass = DrillConstants.class)
public class DrillEndpoint extends DefaultPollingEndpoint {

    @UriPath(description = "Host name or IP address")
    @Metadata(required = true)
    private String host;
    @UriParam(description = "Port number")
    @Metadata(required = false, defaultValue = "2181")
    private Integer port = 2181;
    @UriParam(description = "Drill directory", defaultValue = "")
    private String directory = "";
    @UriParam(defaultValue = "")
    private String clusterId = "";
    @UriParam(defaultValue = "ZK", enums = "ZK,DRILLBIT")
    private DrillConnectionMode mode = DrillConnectionMode.ZK;

    /**
     * creates a drill endpoint
     *
     * @param uri       the endpoint uri
     * @param component the component
     */
    public DrillEndpoint(String uri, DrillComponent component) {
        super(uri, component);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("DrillConsumer is not supported!");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DrillProducer(this);
    }

    public String toJDBCUri() {
        String url = "jdbc:drill:" + mode.name().toLowerCase() + "=" + host + ":" + port;
        if (mode.equals(DrillConnectionMode.ZK)) {
            if (StringUtils.isNotBlank(directory)) {
                url += "/" + directory;
            }
            if (StringUtils.isNotBlank(clusterId)) {
                url += "/" + clusterId;
            }
        }

        return url;
    }

    public List<?> queryForList(ResultSet rs) throws SQLException {
        ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();
        RowMapperResultSetExtractor<Map<String, Object>> mapper = new RowMapperResultSetExtractor<>(rowMapper);
        return mapper.extractData(rs);
    }

    public String getHost() {
        return host;
    }

    /**
     * ZooKeeper host name or IP address. Use local instead of a host name or IP address to connect to the local
     * Drillbit
     *
     * @param host
     */
    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * ZooKeeper port number
     *
     * @param port
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDirectory() {
        return directory;
    }

    /**
     * Drill directory in ZooKeeper
     *
     * @param directory
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getClusterId() {
        return clusterId;
    }

    /**
     * Cluster ID https://drill.apache.org/docs/using-the-jdbc-driver/#determining-the-cluster-id
     *
     * @param clusterId
     */
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    /**
     * Connection mode: zk: Zookeeper drillbit: Drillbit direct connection
     * https://drill.apache.org/docs/using-the-jdbc-driver/
     *
     * @return
     */
    public DrillConnectionMode getMode() {
        return mode;
    }

    public void setMode(DrillConnectionMode mode) {
        this.mode = mode;
    }

}
