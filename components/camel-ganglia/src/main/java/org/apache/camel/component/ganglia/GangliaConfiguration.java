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

import java.io.IOException;
import java.net.URI;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

import info.ganglia.gmetric4j.gmetric.GMetric;
import info.ganglia.gmetric4j.gmetric.GMetricSlope;
import info.ganglia.gmetric4j.gmetric.GMetricType;

/**
 * @version 
 */
@UriParams
public class GangliaConfiguration implements Cloneable {

    public final static String DEFAULT_DESTINATION = "239.2.11.71";
    public final static int DEFAULT_PORT = 8649;
    public final static GMetric.UDPAddressingMode DEFAULT_MODE = GMetric.UDPAddressingMode.MULTICAST;
    public final static int DEFAULT_TTL = 5;
    public final static boolean DEFAULT_WIRE_FORMAT_31X = true;
    public final static String DEFAULT_GROUP_NAME = "Java";
    public final static String DEFAULT_METRIC_NAME = "metric";
    public final static GMetricType DEFAULT_TYPE = GMetricType.STRING;
    public final static GMetricSlope DEFAULT_SLOPE = GMetricSlope.BOTH;
    public final static String DEFAULT_UNITS = "";
    public final static int DEFAULT_TMAX = 60;
    public final static int DEFAULT_DMAX = 0;

    @UriPath
    private String host = DEFAULT_DESTINATION;

    @UriPath
    private int port = DEFAULT_PORT;

    @UriParam
    private GMetric.UDPAddressingMode mode = DEFAULT_MODE;

    @UriParam
    private int ttl = DEFAULT_TTL;

    @UriParam
    private boolean wireFormat31x = DEFAULT_WIRE_FORMAT_31X;

    @UriParam
    private String spoofHostname;

    @UriParam
    private String groupName = DEFAULT_GROUP_NAME;

    @UriParam
    private String prefix = null;

    @UriParam
    private String metricName = DEFAULT_METRIC_NAME;

    @UriParam
    private GMetricType type = DEFAULT_TYPE;

    @UriParam
    private GMetricSlope slope = DEFAULT_SLOPE;

    @UriParam
    private String units = DEFAULT_UNITS;

    @UriParam
    private int tmax = DEFAULT_TMAX;

    @UriParam
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
            return new GMetric(host, port, mode, ttl, wireFormat31x,
                null, spoofHostname);
        } catch(IOException ex) {
            throw new RuntimeCamelException("Failed to initialize Ganglia", ex);
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public GMetric.UDPAddressingMode getMode() {
        return mode;
    }

    public void setMode(GMetric.UDPAddressingMode mode) {
        this.mode = mode;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public boolean getWireFormat31x() {
        return wireFormat31x;
    }

    public void setWireFormat31x(boolean wireFormat31x) {
        this.wireFormat31x = wireFormat31x;
    }

    public String getSpoofHostname() {
        return spoofHostname;
    }

    public void setSpoofHostname(String spoofHostname) {
        this.spoofHostname = spoofHostname;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public GMetricType getType() {
        return type;
    }

    public void setType(GMetricType type) {
        this.type = type;
    }

    public GMetricSlope getSlope() {
        return slope;
    }

    public void setSlope(GMetricSlope slope) {
        this.slope = slope;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public int getTmax() {
        return tmax;
    }

    public void setTmax(int tmax) {
        this.tmax = tmax;
    }

    public int getDmax() {
        return dmax;
    }

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
