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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.support.replication.ReplicationType;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;


@UriParams
public class ElasticsearchConfiguration {

    public static final String PARAM_OPERATION = "operation";
    public static final String OPERATION_INDEX = "INDEX";
    public static final String OPERATION_BULK = "BULK";
    public static final String OPERATION_BULK_INDEX = "BULK_INDEX";
    public static final String OPERATION_GET_BY_ID = "GET_BY_ID";
    public static final String OPERATION_DELETE = "DELETE";
    public static final String OPERATION_SEARCH = "SEARCH";
    public static final String PARAM_INDEX_ID = "indexId";
    public static final String PARAM_DATA = "data";
    public static final String PARAM_INDEX_NAME = "indexName";
    public static final String PARAM_INDEX_TYPE = "indexType";
    public static final String PARAM_CONSISTENCY_LEVEL = "consistencyLevel";
    public static final String PARAM_REPLICATION_TYPE = "replicationType";
    public static final String TRANSPORT_ADDRESSES = "transportAddresses";
    public static final String PROTOCOL = "elasticsearch";
    private static final String LOCAL_NAME = "local";
    private static final String IP = "ip";
    private static final String PORT = "port";
    private static final Integer DEFAULT_PORT = 9300;
    private static final WriteConsistencyLevel DEFAULT_CONSISTENCY_LEVEL = WriteConsistencyLevel.DEFAULT;
    private static final ReplicationType DEFAULT_REPLICATION_TYPE = ReplicationType.DEFAULT;
    private static final String TRANSPORT_ADDRESSES_SEPARATOR_REGEX = ",";
    private static final String IP_PORT_SEPARATOR_REGEX = ":";

    private URI uri;
    @UriPath(description = "Name of cluster or use local for local mode")
    @Metadata(required = "true")
    private String clusterName;
    @UriParam
    private String protocolType;
    @UriParam
    private String authority;
    @UriParam
    private String indexName;
    @UriParam
    private String indexType;
    @UriParam
    private WriteConsistencyLevel consistencyLevel;
    @UriParam
    private ReplicationType replicationType;
    @UriParam
    private boolean local;
    @UriParam
    private Boolean data;
    @UriParam(enums = "INDEX,BULK,BULK_INDEX,GET_BY_ID,DELETE")
    private String operation;
    @UriParam
    private String ip;
    @UriParam
    private List<InetSocketTransportAddress> transportAddresses;
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
        consistencyLevel = parseConsistencyLevel(parameters);
        replicationType = parseReplicationType(parameters);

        ip = (String)parameters.remove(IP);
        transportAddresses = parseTransportAddresses((String) parameters.remove(TRANSPORT_ADDRESSES));

        String portParam = (String) parameters.remove(PORT);
        port = portParam == null ? DEFAULT_PORT : Integer.valueOf(portParam);
    }

    private ReplicationType parseReplicationType(Map<String, Object> parameters) {
        Object replicationTypeParam = parameters.remove(PARAM_REPLICATION_TYPE);
        if (replicationTypeParam != null) {
            return ReplicationType.valueOf(replicationTypeParam.toString());
        } else {
            return DEFAULT_REPLICATION_TYPE;
        }
    }

    private WriteConsistencyLevel parseConsistencyLevel(Map<String, Object> parameters) {
        Object consistencyLevelParam = parameters.remove(PARAM_CONSISTENCY_LEVEL);
        if (consistencyLevelParam != null) {
            return WriteConsistencyLevel.valueOf(consistencyLevelParam.toString());
        } else {
            return DEFAULT_CONSISTENCY_LEVEL;
        }
    }

    private List<InetSocketTransportAddress> parseTransportAddresses(String ipsString) {
        if (ipsString == null || ipsString.isEmpty()) {
            return null;
        }
        List<String> addressesStr = Arrays.asList(ipsString.split(TRANSPORT_ADDRESSES_SEPARATOR_REGEX));
        List<InetSocketTransportAddress> addressesTrAd = new ArrayList<>(addressesStr.size());
        for (String address : addressesStr) {
            String[] split = address.split(IP_PORT_SEPARATOR_REGEX);
            String hostname;
            if (split.length > 0)
                hostname = split[0];
            else
                throw new IllegalArgumentException();
            Integer port = (split.length > 1 ? Integer.parseInt(split[1]) : DEFAULT_PORT);
            addressesTrAd.add(new InetSocketTransportAddress(hostname, port));
        }
        return addressesTrAd;
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

    public List<InetSocketTransportAddress> getTransportAddresses() {
        return transportAddresses;
    }

    public void setTransportAddresses(List<InetSocketTransportAddress> transportAddresses) {
        this.transportAddresses = transportAddresses;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setConsistencyLevel(WriteConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    public WriteConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    public void setReplicationType(ReplicationType replicationType) {
        this.replicationType = replicationType;
    }

    public ReplicationType getReplicationType() {
        return replicationType;
    }

}
