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
package org.apache.camel.component.tahu;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;

/**
 * Publish the Sparkplug B messages of Edge Nodes and Devices to MQTT servers using Eclipse Tahu.
 */
@UriEndpoint(firstVersion = "4.8.0",
             scheme = TahuConstants.EDGE_NODE_SCHEME,
             title = "Tahu Edge Node / Device",
             syntax = TahuConstants.EDGE_NODE_ENDPOINT_URI_SYNTAX,
             alternativeSyntax = TahuConstants.DEVICE_ENDPOINT_URI_SYNTAX,
             producerOnly = true,
             category = { Category.MESSAGING, Category.IOT, Category.MONITORING },
             headersClass = TahuConstants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.POJO, Carrier.TEXT, Carrier.BYTES, Carrier.NULL },
                                  description = "A Tahu SparkplugBPayload with the metrics to publish. For any other body"
                                                + " the metrics are made from the CamelTahuMetric.<name> headers, and the"
                                                + " body, converted to bytes, is the body of the payload (no body gives a"
                                                + " payload without body). A body that does not convert to bytes fails"),
           producerOut = @BodyType(Carrier.UNCHANGED))
public class TahuEdgeEndpoint extends TahuDefaultEndpoint implements HeaderFilterStrategyAware {

    @UriPath(label = "producer", description = "The Sparkplug group ID of the Edge Node.")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME, required = true)
    private final String groupId;

    @UriPath(label = "producer", description = "The Sparkplug Edge Node ID. With the groupId, it identifies the Edge Node.")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME, required = true)
    private final String edgeNode;

    @UriPath(label = "producer",
             description = "The Sparkplug device ID of a Device of the Edge Node. With it, the endpoint is a Device endpoint;"
                           + " without it, an Edge Node endpoint.")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private final String deviceId;

    @UriParam(label = "producer",
              description = "The host ID of the primary Host Application of the Edge Node. If set, the Edge Node waits for"
                            + " the STATE message that says this Host Application is online before it publishes its birth"
                            + " messages, and disconnects from a server when it goes offline. Used by an Edge Node endpoint"
                            + " only.")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private String primaryHostId;

    @UriParam(label = "producer",
              description = "The device IDs of the Devices of the Edge Node, as a comma-separated list. Used by an Edge Node"
                            + " endpoint only.")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private String deviceIds;

    @UriParam(label = "producer",
              description = "The Tahu SparkplugBPayloadMap with every metric of the Edge Node or Device: its name, data"
                            + " type and initial value. The NBIRTH or DBIRTH message has these metrics, and the producer"
                            + " updates their values with each payload it publishes, so a later birth message has the last"
                            + " values.")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME, required = true)
    private SparkplugBPayloadMap metricDataTypePayloadMap;

    @UriParam(label = "producer,advanced",
              description = "Whether to use metric aliases: Tahu gives each metric a generated alias in the birth"
                            + " messages, and the data messages carry the alias instead of the name. Used by an Edge Node"
                            + " endpoint only.",
              defaultValue = "false")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private boolean useAliases = false;

    @UriParam(label = "producer,advanced",
              description = "To use a custom HeaderFilterStrategy to filter headers used as Sparkplug metrics",
              defaultValueNote = "Defaults to sending all Camel Message headers with name prefixes of \""
                                 + TahuConstants.METRIC_HEADER_PREFIX + "\", including those with null values")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private volatile HeaderFilterStrategy headerFilterStrategy;

    @UriParam(label = "producer,advanced",
              description = "The org.eclipse.tahu.message.BdSeqManager that keeps the birth-death sequence number (bdSeq)"
                            + " of the Edge Node from one MQTT session to the next. If not set, a CamelBdSeqManager keeps it"
                            + " in a file below the bdSeqNumPath directory. Used by an Edge Node endpoint only.",
              defaultValue = "org.apache.camel.component.tahu.CamelBdSeqManager")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private volatile BdSeqManager bdSeqManager;

    @UriParam(label = "producer,advanced",
              description = "The directory in which the default bdSeqManager keeps the next birth-death sequence number"
                            + " of the Edge Node, in the file groupId/edgeNode-bdSeqNum below it, named after the IDs of the"
                            + " Edge Node. The process must be able to write to it. Not used when the bdSeqManager option is"
                            + " set.",
              defaultValue = "${sys:java.io.tmpdir}/CamelTahuTemp")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private String bdSeqNumPath;

    private final EdgeNodeDescriptor edgeNodeDescriptor;

    TahuEdgeEndpoint(String uri, TahuDefaultComponent component, TahuConfiguration configuration, String groupId,
                     String edgeNode, String deviceId) {
        super(uri, component, configuration);

        this.groupId = ObjectHelper.notNullOrEmpty(groupId, "groupId");
        this.edgeNode = ObjectHelper.notNullOrEmpty(edgeNode, "edgeNode");

        // Device ID can only be null or non-empty
        this.deviceId = (deviceId != null && deviceId.length() == 0) ? null : deviceId;

        if (ObjectHelper.isNotEmpty(deviceId)) {
            edgeNodeDescriptor = new DeviceDescriptor(groupId, edgeNode, deviceId);
        } else {
            edgeNodeDescriptor = new EdgeNodeDescriptor(groupId, edgeNode);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        TahuEdgeProducer.Builder producerBuilder = new TahuEdgeProducer.Builder(this)
                .groupId(ObjectHelper.notNullOrEmpty(groupId, "groupId"))
                .edgeNode(ObjectHelper.notNullOrEmpty(edgeNode, "edgeNode"));

        ObjectHelper.ifNotEmpty(deviceId, producerBuilder::deviceId);

        return producerBuilder.build();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from this endpoint");
    }

    public EdgeNodeDescriptor getEdgeNodeDescriptor() {
        return edgeNodeDescriptor;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getEdgeNode() {
        return edgeNode;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getPrimaryHostId() {
        return primaryHostId;
    }

    public void setPrimaryHostId(String primaryHostId) {
        this.primaryHostId = primaryHostId;
    }

    public String getDeviceIds() {
        return deviceIds;
    }

    public void setDeviceIds(String deviceIds) {
        this.deviceIds = deviceIds;
    }

    public List<String> getDeviceIdList() {
        return Arrays.asList(deviceIds.split(","));
    }

    public SparkplugBPayloadMap getMetricDataTypePayloadMap() {
        return metricDataTypePayloadMap;
    }

    public void setMetricDataTypePayloadMap(SparkplugBPayloadMap metricDataTypePayloadMap) {
        this.metricDataTypePayloadMap = metricDataTypePayloadMap;
    }

    public boolean isUseAliases() {
        return useAliases;
    }

    public void setUseAliases(boolean useAliases) {
        this.useAliases = useAliases;
    }

    public BdSeqManager getBdSeqManager() {
        return bdSeqManager;
    }

    public void setBdSeqManager(BdSeqManager bdSeqManager) {
        this.bdSeqManager = bdSeqManager;
    }

    public String getBdSeqNumPath() {
        return bdSeqNumPath;
    }

    public void setBdSeqNumPath(String bdSeqNumPath) {
        this.bdSeqNumPath = bdSeqNumPath;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        HeaderFilterStrategy existingStrategy = this.headerFilterStrategy;
        if (existingStrategy == null) {
            DefaultHeaderFilterStrategy strategy = new DefaultHeaderFilterStrategy();
            this.headerFilterStrategy = existingStrategy = strategy;

            strategy.setFilterOnMatch(false);

            strategy.setOutFilter((String) null);
            strategy.setOutFilterPattern((String) null);
            strategy.setOutFilterStartsWith(TahuConstants.METRIC_HEADER_PREFIX);

            strategy.setAllowNullValues(true);
        }

        return existingStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }
}
