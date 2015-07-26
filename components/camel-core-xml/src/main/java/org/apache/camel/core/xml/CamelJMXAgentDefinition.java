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

import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.model.IdentifiedType;

/**
 * The JAXB type class for the configuration of jmxAgent
 *
 * @version 
 */
@XmlRootElement(name = "jmxAgent")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelJMXAgentDefinition extends IdentifiedType {

    /**
     * Disable JMI (default false)
     */
    @XmlAttribute
    private String disabled = "false";

    /**
     * Only register processor if a custom id was defined for it.
     */
    @XmlAttribute
    private String onlyRegisterProcessorWithCustomId = "false";

    /**
     * RMI connector registry port (default 1099)
     */
    @XmlAttribute
    private String registryPort;

    /**
     * RMI connector server port (default -1 not used)
     */
    @XmlAttribute
    private String connectorPort;

    /**
     * MBean server default domain name (default org.apache.camel)
     */
    @XmlAttribute
    private String mbeanServerDefaultDomain;

    /**
     * MBean object domain name (default org.apache.camel)
     */
    @XmlAttribute
    private String mbeanObjectDomainName;

    /**
     * JMX Service URL path (default /jmxrmi)
     */
    @XmlAttribute
    private String serviceUrlPath;

    /**
     * A flag that indicates whether the agent should be created
     */
    @XmlAttribute
    private String createConnector = "false";

    /**
     * A flag that indicates whether the platform mbean server should be used
     */
    @XmlAttribute
    private String usePlatformMBeanServer = "true";

    /**
     * A flag that indicates whether to register mbeans always
     */
    @XmlAttribute
    private String registerAlways;

    /**
     * A flag that indicates whether to register mbeans when starting new routes
     */
    @XmlAttribute
    private String registerNewRoutes = "true";

    /**
     * Level of granularity for performance statistics enabled
     */
    @XmlAttribute
    private String statisticsLevel = ManagementStatisticsLevel.Default.name();

    /**
     * A flag that indicates whether Load statistics is enabled
     */
    @XmlAttribute
    private String loadStatisticsEnabled;

    /**
     * A flag that indicates whether to include hostname in JMX MBean names.
     */
    @XmlAttribute
    private String includeHostName = "false";

    /**
     * A flag that indicates whether to remove detected sensitive information (such as passwords) from MBean names and attributes.
     */
    @XmlAttribute
    private String mask = "false";

    public String getDisabled() {
        return disabled;
    }

    public void setDisabled(String disabled) {
        this.disabled = disabled;
    }

    public String getOnlyRegisterProcessorWithCustomId() {
        return onlyRegisterProcessorWithCustomId;
    }

    public void setOnlyRegisterProcessorWithCustomId(String onlyRegisterProcessorWithCustomId) {
        this.onlyRegisterProcessorWithCustomId = onlyRegisterProcessorWithCustomId;
    }

    public String getRegistryPort() {
        return registryPort;
    }

    public void setRegistryPort(String registryPort) {
        this.registryPort = registryPort;
    }

    public String getConnectorPort() {
        return connectorPort;
    }

    public void setConnectorPort(String connectorPort) {
        this.connectorPort = connectorPort;
    }

    public String getMbeanServerDefaultDomain() {
        return mbeanServerDefaultDomain;
    }

    public void setMbeanServerDefaultDomain(String mbeanServerDefaultDomain) {
        this.mbeanServerDefaultDomain = mbeanServerDefaultDomain;
    }

    public String getMbeanObjectDomainName() {
        return mbeanObjectDomainName;
    }

    public void setMbeanObjectDomainName(String mbeanObjectDomainName) {
        this.mbeanObjectDomainName = mbeanObjectDomainName;
    }

    public String getServiceUrlPath() {
        return serviceUrlPath;
    }

    public void setServiceUrlPath(String serviceUrlPath) {
        this.serviceUrlPath = serviceUrlPath;
    }

    public String getCreateConnector() {
        return createConnector;
    }

    public void setCreateConnector(String createConnector) {
        this.createConnector = createConnector;
    }

    public String getUsePlatformMBeanServer() {
        return usePlatformMBeanServer;
    }

    public void setUsePlatformMBeanServer(String usePlatformMBeanServer) {
        this.usePlatformMBeanServer = usePlatformMBeanServer;
    }

    public String getStatisticsLevel() {
        return statisticsLevel;
    }

    public void setStatisticsLevel(String statisticsLevel) {
        this.statisticsLevel = statisticsLevel;
    }

    public String getRegisterAlways() {
        return registerAlways;
    }

    public void setRegisterAlways(String registerAlways) {
        this.registerAlways = registerAlways;
    }

    public String getRegisterNewRoutes() {
        return registerNewRoutes;
    }

    public void setRegisterNewRoutes(String registerNewRoutes) {
        this.registerNewRoutes = registerNewRoutes;
    }

    public String getLoadStatisticsEnabled() {
        return loadStatisticsEnabled;
    }

    public void setLoadStatisticsEnabled(String loadStatisticsEnabled) {
        this.loadStatisticsEnabled = loadStatisticsEnabled;
    }

    public String getIncludeHostName() {
        return includeHostName;
    }

    public void setIncludeHostName(String includeHostName) {
        this.includeHostName = includeHostName;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CamelJMXAgent[");
        sb.append("usePlatformMBeanServer=").append(usePlatformMBeanServer);
        if (createConnector != null) {
            sb.append(", createConnector=").append(createConnector);
        }
        if (connectorPort != null) {
            sb.append(", connectorPort=").append(connectorPort);
        }
        if (registryPort != null) {
            sb.append(", registryPort=").append(registryPort);
        }
        if (serviceUrlPath != null) {
            sb.append(", serviceUrlPath=").append(serviceUrlPath);
        }
        if (mbeanServerDefaultDomain != null) {
            sb.append(", mbeanServerDefaultDomain=").append(mbeanServerDefaultDomain);
        }
        if (mbeanObjectDomainName != null) {
            sb.append(", mbeanObjectDomainName=").append(mbeanObjectDomainName);
        }
        if (statisticsLevel != null) {
            sb.append(", statisticsLevel=").append(statisticsLevel);
        }
        if (loadStatisticsEnabled != null) {
            sb.append(", loadStatisticsEnabled=").append(loadStatisticsEnabled);
        }
        if (onlyRegisterProcessorWithCustomId != null) {
            sb.append(", onlyRegisterProcessorWithCustomId=").append(onlyRegisterProcessorWithCustomId);
        }
        if (registerAlways != null) {
            sb.append(", registerAlways=").append(registerAlways);
        }
        if (registerNewRoutes != null) {
            sb.append(", registerNewRoutes=").append(registerNewRoutes);
        }
        if (includeHostName != null) {
            sb.append(", includeHostName=").append(includeHostName);
        }
        if (mask != null) {
            sb.append(", mask=").append(mask);
        }
        sb.append("]");
        return sb.toString();
    }

}