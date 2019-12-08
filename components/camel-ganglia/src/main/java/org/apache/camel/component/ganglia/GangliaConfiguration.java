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
package org.apache.camel.component.ganglia;

import java.io.IOException;
import java.net.URI;

import info.ganglia.gmetric4j.gmetric.GMetric;
import info.ganglia.gmetric4j.gmetric.GMetricSlope;
import info.ganglia.gmetric4j.gmetric.GMetricType;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GangliaConfiguration implements Cloneable {

    public static final String DEFAULT_DESTINATION = "239.2.11.71";
    public static final int DEFAULT_PORT = 8649;
    public static final GMetric.UDPAddressingMode DEFAULT_MODE = GMetric.UDPAddressingMode.MULTICAST;
    public static final int DEFAULT_TTL = 5;
    public static final boolean DEFAULT_WIRE_FORMAT_31X = true;
    public static final String DEFAULT_GROUP_NAME = "Java";
    public static final String DEFAULT_METRIC_NAME = "metric";
    public static final GMetricType DEFAULT_TYPE = GMetricType.STRING;
    public static final GMetricSlope DEFAULT_SLOPE = GMetricSlope.BOTH;
    public static final String DEFAULT_UNITS = "";
    public static final int DEFAULT_TMAX = 60;
    public static final int DEFAULT_DMAX = 0;

    @UriPath(defaultValue = DEFAULT_DESTINATION)
    private String host = DEFAULT_DESTINATION;

    @UriPath(defaultValue = "" + DEFAULT_PORT)
    private int port = DEFAULT_PORT;

    @UriParam(defaultValue = "MULTICAST", enums = "MULTICAST,UNICAST")
    private GMetric.UDPAddressingMode mode = DEFAULT_MODE;

    @UriParam(defaultValue = "5")
    private int ttl = DEFAULT_TTL;

    @UriParam(defaultValue = "true")
    private boolean wireFormat31x = DEFAULT_WIRE_FORMAT_31X;

    @UriParam
    private String spoofHostname;

    @UriParam(defaultValue = "java")
    private String groupName = DEFAULT_GROUP_NAME;

    @UriParam
    private String prefix;

    @UriParam(defaultValue = "metric")
    private String metricName = DEFAULT_METRIC_NAME;

    @UriParam(defaultValue = "STRING", enums = "STRING,INT8,UINT8,INT16,UINT16,INT32,UINT32,FLOAT,DOUBLE")
    private GMetricType type = DEFAULT_TYPE;

    @UriParam(defaultValue = "BOTH", enums = "ZERO,POSITIVE,NEGATIVE,BOTH")
    private GMetricSlope slope = DEFAULT_SLOPE;

    @UriParam
    private String units = DEFAULT_UNITS;

    @UriParam(defaultValue = "60")
    private int tmax = DEFAULT_TMAX;

    @UriParam(defaultValue = "0")
    private int dmax = DEFAULT_DMAX;

    /**
     * Returns a copy of this configuration
     */
    public GangliaConfiguration copy() {
        try {
            GangliaConfiguration copy = (GangliaConfiguration) clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void configure(URI uri) {
        String value = uri.getHost();
        if (value != null) {
            setHost(value);
        }
        int port = uri.getPort();
        if (port > 0) {
            setPort(port);
        }
    }

    public GMetric createGMetric() {
        try {
            return new GMetric(host, port, mode, ttl, wireFormat31x, null, spoofHostname);
        } catch (IOException ex) {
            throw new RuntimeCamelException("Failed to initialize Ganglia", ex);
        }
    }

    public String getHost() {
        return host;
    }

    /**
     * Host name for Ganglia server
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port for Ganglia server
     */
    public void setPort(int port) {
        this.port = port;
    }

    public GMetric.UDPAddressingMode getMode() {
        return mode;
    }

    /**
     * Send the UDP metric packets using MULTICAST or UNICAST
     */
    public void setMode(GMetric.UDPAddressingMode mode) {
        this.mode = mode;
    }

    public int getTtl() {
        return ttl;
    }

    /**
     * If using multicast, set the TTL of the packets
     */
    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public boolean getWireFormat31x() {
        return wireFormat31x;
    }

    /**
     * Use the wire format of Ganglia 3.1.0 and later versions.  Set this to false to use Ganglia 3.0.x or earlier.
     */
    public void setWireFormat31x(boolean wireFormat31x) {
        this.wireFormat31x = wireFormat31x;
    }

    public String getSpoofHostname() {
        return spoofHostname;
    }

    /**
     * Spoofing information IP:hostname
     */
    public void setSpoofHostname(String spoofHostname) {
        this.spoofHostname = spoofHostname;
    }

    public String getGroupName() {
        return groupName;
    }

    /**
     * The group that the metric belongs to.
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Prefix the metric name with this string and an underscore.
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getMetricName() {
        return metricName;
    }

    /**
     * The name to use for the metric.
     */
    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public GMetricType getType() {
        return type;
    }

    /**
     * The type of value
     */
    public void setType(GMetricType type) {
        this.type = type;
    }

    public GMetricSlope getSlope() {
        return slope;
    }

    /**
     * The slope
     */
    public void setSlope(GMetricSlope slope) {
        this.slope = slope;
    }

    public String getUnits() {
        return units;
    }

    /**
     * Any unit of measurement that qualifies the metric, e.g. widgets, litres, bytes.
     * Do not include a prefix such as k (kilo) or m (milli), other tools may scale the units later.
     * The value should be unscaled.
     */
    public void setUnits(String units) {
        this.units = units;
    }

    public boolean isWireFormat31x() {
        return wireFormat31x;
    }

    public int getTmax() {
        return tmax;
    }

    /**
     * Maximum time in seconds that the value can be considered current.
     * After this, Ganglia considers the value to have expired.
     */
    public void setTmax(int tmax) {
        this.tmax = tmax;
    }

    public int getDmax() {
        return dmax;
    }

    /**
     * Minumum time in seconds before Ganglia will purge the metric value if it expires.
     * Set to 0 and the value will remain in Ganglia indefinitely until a gmond agent restart.
     */
    public void setDmax(int dmax) {
        this.dmax = dmax;
    }

    @Override
    public String toString() {
        return "GangliaConfiguration["
                + "host=" + host + ":" + port
                + ", mode=" + mode
                + ", ttl=" + ttl
                + ", wireFormat31x=" + wireFormat31x
                + ", spoofHostname=" + spoofHostname
                + ", groupName=" + groupName
                + ", prefix=" + prefix
                + ", metricName=" + metricName
                + ", type=" + type
                + ", slope=" + slope
                + ", units=" + units
                + ", tmax=" + tmax
                + ", dmax=" + dmax
                + "]";
    }

}
