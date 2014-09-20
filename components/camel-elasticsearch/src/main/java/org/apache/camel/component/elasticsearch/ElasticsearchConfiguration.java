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
package org.apache.camel.component.elasticsearch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;


@UriParams
public class ElasticsearchConfiguration {

    public static final String PARAM_OPERATION = "operation";
    public static final String OPERATION_INDEX = "INDEX";
    public static final String OPERATION_BULK_INDEX = "BULK_INDEX";
    public static final String OPERATION_GET_BY_ID = "GET_BY_ID";
    public static final String OPERATION_DELETE = "DELETE";
    public static final String PARAM_INDEX_ID = "indexId";
    public static final String PARAM_DATA = "data";
    public static final String PARAM_INDEX_NAME = "indexName";
    public static final String PARAM_INDEX_TYPE = "indexType";
    public static final String PROTOCOL = "elasticsearch";
    private static final String LOCAL_NAME = "local";
    private static final String IP = "ip";
    private static final String PORT = "port";
    private static final Integer DEFAULT_PORT = 9300;

    private URI uri;
    @UriParam
    private String protocolType;
    @UriParam
    private String authority;
    @UriParam
    private String clusterName;
    @UriParam
    private String indexName;
    @UriParam
    private String indexType;
    @UriParam
    private boolean local;
    @UriParam
    private Boolean data;
    @UriParam
    private String operation;
    @UriParam
    private String ip;
    @UriParam
    private Integer port;

    public ElasticsearchConfiguration(URI uri, Map<String, Object> parameters) throws Exception {
        String protocol = uri.getScheme();

        if (!protocol.equalsIgnoreCase(PROTOCOL)) {
            throw new IllegalArgumentException("unrecognized elasticsearch protocol: " + protocol + " for uri: " + uri);
        }
        setUri(uri);
        setAuthority(uri.getAuthority());
        if (!isValidAuthority()) {
            throw new URISyntaxException(uri.toASCIIString(), "incorrect URI syntax specified for the elasticsearch endpoint."
                                                              + "please specify the syntax as \"elasticsearch:[Cluster Name | 'local']?[Query]\"");
        }

        if (LOCAL_NAME.equals(getAuthority())) {
            setLocal(true);
            setClusterName(null);
        } else {
            setLocal(false);
            setClusterName(getAuthority());
        }

        data = toBoolean(parameters.remove(PARAM_DATA));

        if (data == null) {
            data = local;
        }

        if (local && !data) {
            throw new IllegalArgumentException("invalid to use local node without data");
        }

        indexName = (String)parameters.remove(PARAM_INDEX_NAME);
        indexType = (String)parameters.remove(PARAM_INDEX_TYPE);
        operation = (String)parameters.remove(PARAM_OPERATION);
        ip = (String)parameters.remove(IP);
        String portParam = (String) parameters.remove(PORT);
        port = portParam == null ? DEFAULT_PORT : Integer.valueOf(portParam);
    }

    protected Boolean toBoolean(Object string) {
        if ("true".equals(string)) {
            return true;
        } else if ("false".equals(string)) {
            return false;
        } else {
            return null;
        }
    }

    public Node buildNode() {
        NodeBuilder builder = nodeBuilder().local(isLocal()).data(isData());
        if (!isLocal() && getClusterName() != null) {
            builder.clusterName(getClusterName());
        }
        return builder.node();
    }

    private boolean isValidAuthority() throws URISyntaxException {
        if (authority.contains(":")) {
            return false;
        }
        return true;

    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public boolean isData() {
        return data;
    }

    public void setData(boolean data) {
        this.data = data;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return this.operation;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

}
