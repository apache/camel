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
 * @version $Revision: 911871 $
 */
@XmlRootElement(name = "jmxAgent")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelJMXAgentDefinition extends IdentifiedType {

    /**
     * Disable JMI (default false)
     */
    @XmlAttribute(required = false)
    private String disabled = "false";

    /**
     * Only register processor if a custom id was defined for it.
     */
    @XmlAttribute(required = false)
    private String onlyRegisterProcessorWithCustomId = "false";

    /**
     * RMI connector registry port (default 1099)
     */
    @XmlAttribute(required = false)
    private String registryPort;

    /**
     * RMI connector server port (default -1 not used)
     */
    @XmlAttribute(required = false)
    private String connectorPort;

    /**
     * MBean server default domain name (default org.apache.camel)
     */
    @XmlAttribute(required = false)
    private String mbeanServerDefaultDomain;

    /**
     * MBean object domain name (default org.apache.camel)
     */
    @XmlAttribute(required = false)
    private String mbeanObjectDomainName;

    /**
     * JMX Service URL path (default /jmxrmi)
     */
    @XmlAttribute(required = false)
    private String serviceUrlPath;

    /**
     * A flag that indicates whether the agent should be created
     */
    @XmlAttribute(required = false)
    private String createConnector = "true";

    /**
     * A flag that indicates whether the platform mbean server should be used
     */
    @XmlAttribute(required = false)
    private String usePlatformMBeanServer = "true";

    /**
     * A flag that indicates whether to register mbeans always
     */
    @XmlAttribute(required = false)
    private String registerAlways;

    /**
     * A flag that indicates whether to register mbeans when starting new routes
     */
    @XmlAttribute(required = false)
    private String registerNewRoutes = "true";

    /**
     * Level of granularity for performance statistics enabled
     */
    @XmlAttribute(required = false)
    private ManagementStatisticsLevel statisticsLevel = ManagementStatisticsLevel.All;

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

    public ManagementStatisticsLevel getStatisticsLevel() {
        return statisticsLevel;
    }

    public void setStatisticsLevel(ManagementStatisticsLevel statisticsLevel) {
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
        if (registerAlways != null) {
            sb.append(", registerAlways=").append(registerAlways);
        }
        if (registerNewRoutes != null) {
            sb.append(", registerNewRoutes=").append(registerNewRoutes);
        }
        sb.append("]");
        return sb.toString();
    }

}