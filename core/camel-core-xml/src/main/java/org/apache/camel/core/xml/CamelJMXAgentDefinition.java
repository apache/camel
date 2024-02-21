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
package org.apache.camel.core.xml;

import java.util.StringJoiner;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.Metadata;

/**
 * JMX configuration.
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "jmxAgent")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelJMXAgentDefinition extends IdentifiedType {

    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String disabled;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String onlyRegisterProcessorWithCustomId;
    @XmlAttribute
    @Metadata(defaultValue = "org.apache.camel")
    private String mbeanServerDefaultDomain;
    @XmlAttribute
    @Metadata(defaultValue = "org.apache.camel")
    private String mbeanObjectDomainName;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String usePlatformMBeanServer;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String registerAlways;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String registerNewRoutes;
    @XmlAttribute
    @Metadata(defaultValue = "Default", enums = "ContextOnly,RoutesOnly,Default")
    private String statisticsLevel;
    @XmlAttribute
    @Metadata(defaultValue = "Default", enums = "ContextOnly,RoutesOnly,Default")
    private String mbeansLevel;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String loadStatisticsEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String endpointRuntimeStatisticsEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String includeHostName;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String useHostIPAddress;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String mask;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String updateRouteEnabled;

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

    public String getMbeansLevel() {
        return mbeansLevel;
    }

    /**
     * Sets the mbeans registration level.
     *
     * The default value is Default.
     */
    public void setMbeansLevel(String mbeansLevel) {
        this.mbeansLevel = mbeansLevel;
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
     * A flag that indicates whether to remove detected sensitive information (such as passwords) from MBean names and
     * attributes.
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

    public String getUpdateRouteEnabled() {
        return updateRouteEnabled;
    }

    /**
     * Sets whether updating routes via JMX is allowed (is default disabled).
     */
    public void setUpdateRouteEnabled(String updateRouteEnabled) {
        this.updateRouteEnabled = updateRouteEnabled;
    }

    @Override
    public String toString() {
        StringJoiner buffer = new StringJoiner(", ", "CamelJMXAgent[", "]");
        if (disabled != null) {
            buffer.add("disabled=" + disabled);
        }
        if (usePlatformMBeanServer != null) {
            buffer.add("usePlatformMBeanServer=" + usePlatformMBeanServer);
        }
        if (mbeanServerDefaultDomain != null) {
            buffer.add("mbeanServerDefaultDomain=" + mbeanServerDefaultDomain);
        }
        if (mbeanObjectDomainName != null) {
            buffer.add("mbeanObjectDomainName=" + mbeanObjectDomainName);
        }
        if (statisticsLevel != null) {
            buffer.add("statisticsLevel=" + statisticsLevel);
        }
        if (loadStatisticsEnabled != null) {
            buffer.add("loadStatisticsEnabled=" + loadStatisticsEnabled);
        }
        if (endpointRuntimeStatisticsEnabled != null) {
            buffer.add("endpointRuntimeStatisticsEnabled=" + endpointRuntimeStatisticsEnabled);
        }
        if (onlyRegisterProcessorWithCustomId != null) {
            buffer.add("onlyRegisterProcessorWithCustomId=" + onlyRegisterProcessorWithCustomId);
        }
        if (registerAlways != null) {
            buffer.add("registerAlways=" + registerAlways);
        }
        if (registerNewRoutes != null) {
            buffer.add("registerNewRoutes=" + registerNewRoutes);
        }
        if (includeHostName != null) {
            buffer.add("includeHostName=" + includeHostName);
        }
        if (useHostIPAddress != null) {
            buffer.add("useHostIPAddress=" + useHostIPAddress);
        }
        if (mask != null) {
            buffer.add("mask=" + mask);
        }
        if (updateRouteEnabled != null) {
            buffer.add("updateRouteEnabled=" + updateRouteEnabled);
        }
        return buffer.toString();
    }

}
