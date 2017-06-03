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
package org.apache.camel.core.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CollectionStringBuffer;

/**
 * JMX configuration.
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "jmxAgent")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelJMXAgentDefinition extends IdentifiedType {

    @XmlAttribute @Metadata(defaultValue = "false")
    private String disabled;
    @XmlAttribute @Metadata(defaultValue = "false")
    private String onlyRegisterProcessorWithCustomId;
    @XmlAttribute @Metadata(defaultValue = "1099")
    private String registryPort;
    @XmlAttribute @Metadata(defaultValue = "-1")
    private String connectorPort;
    @XmlAttribute @Metadata(defaultValue = "org.apache.camel")
    private String mbeanServerDefaultDomain;
    @XmlAttribute @Metadata(defaultValue = "org.apache.camel")
    private String mbeanObjectDomainName;
    @XmlAttribute @Metadata(defaultValue = "/jmxrmi")
    private String serviceUrlPath;
    @XmlAttribute @Metadata(defaultValue = "false")
    private String createConnector;
    @XmlAttribute @Metadata(defaultValue = "true")
    private String usePlatformMBeanServer;
    @XmlAttribute @Metadata(defaultValue = "false")
    private String registerAlways;
    @XmlAttribute @Metadata(defaultValue = "false")
    private String registerNewRoutes;
    @XmlAttribute @Metadata(defaultValue = "Default")
    private String statisticsLevel;
    @XmlAttribute @Metadata(defaultValue = "false")
    private String loadStatisticsEnabled;
    @XmlAttribute @Metadata(defaultValue = "true")
    private String endpointRuntimeStatisticsEnabled;
    @XmlAttribute @Metadata(defaultValue = "false")
    private String includeHostName;
    @XmlAttribute @Metadata(defaultValue = "false")
    private String useHostIPAddress;
    @XmlAttribute @Metadata(defaultValue = "true")
    private String mask;

    public String getDisabled() {
        return disabled;
    }

    /**
     * Disable JMI (default false)
     */
    public void setDisabled(String disabled) {
        this.disabled = disabled;
    }

    public String getOnlyRegisterProcessorWithCustomId() {
        return onlyRegisterProcessorWithCustomId;
    }

    /**
     * Only register processor if a custom id was defined for it.
     */
    public void setOnlyRegisterProcessorWithCustomId(String onlyRegisterProcessorWithCustomId) {
        this.onlyRegisterProcessorWithCustomId = onlyRegisterProcessorWithCustomId;
    }

    public String getRegistryPort() {
        return registryPort;
    }

    /**
     * RMI connector registry port (default 1099)
     */
    public void setRegistryPort(String registryPort) {
        this.registryPort = registryPort;
    }

    public String getConnectorPort() {
        return connectorPort;
    }

    /**
     * RMI connector server port (default -1 not used)
     */
    public void setConnectorPort(String connectorPort) {
        this.connectorPort = connectorPort;
    }

    public String getMbeanServerDefaultDomain() {
        return mbeanServerDefaultDomain;
    }

    /**
     * MBean server default domain name (default org.apache.camel)
     */
    public void setMbeanServerDefaultDomain(String mbeanServerDefaultDomain) {
        this.mbeanServerDefaultDomain = mbeanServerDefaultDomain;
    }

    public String getMbeanObjectDomainName() {
        return mbeanObjectDomainName;
    }

    /**
     * MBean object domain name (default org.apache.camel)
     */
    public void setMbeanObjectDomainName(String mbeanObjectDomainName) {
        this.mbeanObjectDomainName = mbeanObjectDomainName;
    }

    public String getServiceUrlPath() {
        return serviceUrlPath;
    }

    /**
     * JMX Service URL path (default /jmxrmi)
     */
    public void setServiceUrlPath(String serviceUrlPath) {
        this.serviceUrlPath = serviceUrlPath;
    }

    public String getCreateConnector() {
        return createConnector;
    }

    /**
     * A flag that indicates whether the agent should be created
     */
    public void setCreateConnector(String createConnector) {
        this.createConnector = createConnector;
    }

    public String getUsePlatformMBeanServer() {
        return usePlatformMBeanServer;
    }

    /**
     * A flag that indicates whether the platform mbean server should be used
     */
    public void setUsePlatformMBeanServer(String usePlatformMBeanServer) {
        this.usePlatformMBeanServer = usePlatformMBeanServer;
    }

    public String getStatisticsLevel() {
        return statisticsLevel;
    }

    /**
     * Level of granularity for performance statistics enabled
     */
    public void setStatisticsLevel(String statisticsLevel) {
        this.statisticsLevel = statisticsLevel;
    }

    public String getRegisterAlways() {
        return registerAlways;
    }

    /**
     * A flag that indicates whether to register mbeans always
     */
    public void setRegisterAlways(String registerAlways) {
        this.registerAlways = registerAlways;
    }

    public String getRegisterNewRoutes() {
        return registerNewRoutes;
    }

    /**
     * A flag that indicates whether to register mbeans when starting new routes
     */
    public void setRegisterNewRoutes(String registerNewRoutes) {
        this.registerNewRoutes = registerNewRoutes;
    }

    public String getLoadStatisticsEnabled() {
        return loadStatisticsEnabled;
    }

    /**
     * A flag that indicates whether Load statistics is enabled
     */
    public void setLoadStatisticsEnabled(String loadStatisticsEnabled) {
        this.loadStatisticsEnabled = loadStatisticsEnabled;
    }

    public String getEndpointRuntimeStatisticsEnabled() {
        return endpointRuntimeStatisticsEnabled;
    }

    /**
     * A flag that indicates whether endpoint runtime statistics is enabled
     */
    public void setEndpointRuntimeStatisticsEnabled(String endpointRuntimeStatisticsEnabled) {
        this.endpointRuntimeStatisticsEnabled = endpointRuntimeStatisticsEnabled;
    }

    public String getIncludeHostName() {
        return includeHostName;
    }

    /**
     * A flag that indicates whether to include hostname in JMX MBean names.
     */
    public void setIncludeHostName(String includeHostName) {
        this.includeHostName = includeHostName;
    }

    public String getMask() {
        return mask;
    }

    /**
     * A flag that indicates whether to remove detected sensitive information (such as passwords) from MBean names and attributes.
     */
    public void setMask(String mask) {
        this.mask = mask;
    }

    public String getUseHostIPAddress() {
        return useHostIPAddress;
    }

    /**
     * A flag that indicates whether to use hostname or IP Address in the service url.
     */
    public void setUseHostIPAddress(String useHostIPAddress) {
        this.useHostIPAddress = useHostIPAddress;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CamelJMXAgent[");

        CollectionStringBuffer csb = new CollectionStringBuffer();
        if (disabled != null) {
            csb.append("disabled=" + disabled);
        }
        if (usePlatformMBeanServer != null) {
            csb.append("usePlatformMBeanServer=" + usePlatformMBeanServer);
        }
        if (createConnector != null) {
            csb.append("createConnector=" + createConnector);
        }
        if (connectorPort != null) {
            csb.append("connectorPort=" + connectorPort);
        }
        if (registryPort != null) {
            csb.append("registryPort=" + registryPort);
        }
        if (serviceUrlPath != null) {
            csb.append("serviceUrlPath=" + serviceUrlPath);
        }
        if (mbeanServerDefaultDomain != null) {
            csb.append("mbeanServerDefaultDomain=" + mbeanServerDefaultDomain);
        }
        if (mbeanObjectDomainName != null) {
            csb.append("mbeanObjectDomainName=" + mbeanObjectDomainName);
        }
        if (statisticsLevel != null) {
            csb.append("statisticsLevel=" + statisticsLevel);
        }
        if (loadStatisticsEnabled != null) {
            csb.append("loadStatisticsEnabled=" + loadStatisticsEnabled);
        }
        if (endpointRuntimeStatisticsEnabled != null) {
            csb.append("endpointRuntimeStatisticsEnabled=" + endpointRuntimeStatisticsEnabled);
        }
        if (onlyRegisterProcessorWithCustomId != null) {
            csb.append("onlyRegisterProcessorWithCustomId=" + onlyRegisterProcessorWithCustomId);
        }
        if (registerAlways != null) {
            csb.append("registerAlways=" + registerAlways);
        }
        if (registerNewRoutes != null) {
            csb.append("registerNewRoutes=" + registerNewRoutes);
        }
        if (includeHostName != null) {
            csb.append("includeHostName=" + includeHostName);
        }
        if (useHostIPAddress != null) {
            csb.append("useHostIPAddress=" + useHostIPAddress);
        }
        if (mask != null) {
            csb.append("mask=" + mask);
        }

        sb.append(csb.toString());
        sb.append("]");
        return sb.toString();
    }

}