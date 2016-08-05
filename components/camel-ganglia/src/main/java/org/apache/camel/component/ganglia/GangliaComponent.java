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
package org.apache.camel.component.ganglia;

import java.net.URI;
import java.util.Map;

import info.ganglia.gmetric4j.gmetric.GMetric;
import info.ganglia.gmetric4j.gmetric.GMetricSlope;
import info.ganglia.gmetric4j.gmetric.GMetricType;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;

public class GangliaComponent extends UriEndpointComponent {

    private GangliaConfiguration configuration;

    public GangliaComponent() {
        super(GangliaEndpoint.class);
        configuration = new GangliaConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        URI url = new URI(uri);

        // must use copy as each endpoint can have different options
        ObjectHelper.notNull(configuration, "configuration");
        GangliaConfiguration config = configuration.copy();
        config.configure(url);
        setProperties(config, parameters);

        GangliaEndpoint endpoint = new GangliaEndpoint(uri, this);
        endpoint.setConfiguration(config);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public GangliaConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared configuration. Properties of the shared configuration can also be set individually.
     */
    public void setConfiguration(GangliaConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getHost() {
        return configuration.getHost();
    }

    /**
     * Host name for Ganglia server
     * @param host
     */
    public void setHost(String host) {
        configuration.setHost(host);
    }

    public int getPort() {
        return configuration.getPort();
    }

    /**
     * Port for Ganglia server
     * @param port
     */
    public void setPort(int port) {
        configuration.setPort(port);
    }

    public GMetric.UDPAddressingMode getMode() {
        return configuration.getMode();
    }

    /**
     * Send the UDP metric packets using MULTICAST or UNICAST
     * @param mode
     */
    public void setMode(GMetric.UDPAddressingMode mode) {
        configuration.setMode(mode);
    }

    public int getTtl() {
        return configuration.getTtl();
    }

    /**
     * If using multicast, set the TTL of the packets
     * @param ttl
     */
    public void setTtl(int ttl) {
        configuration.setTtl(ttl);
    }

    public boolean getWireFormat31x() {
        return configuration.getWireFormat31x();
    }

    /**
     * Use the wire format of Ganglia 3.1.0 and later versions.  Set this to false to use Ganglia 3.0.x or earlier.
     * @param wireFormat31x
     */
    public void setWireFormat31x(boolean wireFormat31x) {
        configuration.setWireFormat31x(wireFormat31x);
    }

    public String getSpoofHostname() {
        return configuration.getSpoofHostname();
    }

    /**
     * Spoofing information IP:hostname
     * @param spoofHostname
     */
    public void setSpoofHostname(String spoofHostname) {
        configuration.setSpoofHostname(spoofHostname);
    }

    public String getGroupName() {
        return configuration.getGroupName();
    }

    /**
     * The group that the metric belongs to.
     * @param groupName
     */
    public void setGroupName(String groupName) {
        configuration.setGroupName(groupName);
    }

    public String getPrefix() {
        return configuration.getPrefix();
    }

    /**
     * Prefix the metric name with this string and an underscore.
     * @param prefix
     */
    public void setPrefix(String prefix) {
        configuration.setPrefix(prefix);
    }

    public String getMetricName() {
        return configuration.getMetricName();
    }

    /**
     * The name to use for the metric.
     * @param metricName
     */
    public void setMetricName(String metricName) {
        configuration.setMetricName(metricName);
    }

    public GMetricType getType() {
        return configuration.getType();
    }

    /**
     * The type of value
     * @param type
     */
    public void setType(GMetricType type) {
        configuration.setType(type);
    }

    public GMetricSlope getSlope() {
        return configuration.getSlope();
    }

    /**
     * The slope
     * @param slope
     */
    public void setSlope(GMetricSlope slope) {
        configuration.setSlope(slope);
    }

    public String getUnits() {
        return configuration.getUnits();
    }

    /**
     * Any unit of measurement that qualifies the metric, e.g. widgets, litres, bytes.
     * Do not include a prefix such as k (kilo) or m (milli), other tools may scale the units later.
     * The value should be unscaled.
     * @param units
     */
    public void setUnits(String units) {
        configuration.setUnits(units);
    }

    public boolean isWireFormat31x() {
        return configuration.isWireFormat31x();
    }

    public int getTmax() {
        return configuration.getTmax();
    }

    /**
     * Maximum time in seconds that the value can be considered current.
     * After this, Ganglia considers the value to have expired.
     * @param tmax
     */
    public void setTmax(int tmax) {
        configuration.setTmax(tmax);
    }

    public int getDmax() {
        return configuration.getDmax();
    }

    /**
     * Minumum time in seconds before Ganglia will purge the metric value if it expires.
     * Set to 0 and the value will remain in Ganglia indefinitely until a gmond agent restart.
     * @param dmax
     */
    public void setDmax(int dmax) {
        configuration.setDmax(dmax);
    }
}
