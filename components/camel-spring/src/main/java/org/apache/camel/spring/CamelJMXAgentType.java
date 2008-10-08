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

package org.apache.camel.spring;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;

/**
 * The JAXB type class for the configuration of jmxAgent
 * @author Willem Jiang
 *
 * @version $Revision$
 */
@XmlRootElement(name = "jmxAgent")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelJMXAgentType extends IdentifiedType {
    /**
     * Disable JMI (default false)
     */
    @XmlAttribute(required = false)
    private Boolean disabled = Boolean.FALSE;
    
    /**
     * RMI connector registry port (default 1099)
     */
    @XmlAttribute(required = false)
    private Integer registryPort;
    
    /**
     * RMI connector server port (default -1 not used)
     */
    @XmlAttribute(required = false)
    private Integer connectorPort;
    
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
    private Boolean createConnector = Boolean.TRUE;
    
    /**
     * A flag that indicates whether the platform mbean server should be used
     */
    @XmlAttribute(required = false)
    private Boolean usePlatformMBeanServer = Boolean.TRUE;

    public Integer getConnectorPort() {
        return connectorPort;
    }
    
    public void setConnectorPort(Integer value) {
        connectorPort = value;
    }

    public Integer getRegistryPort() {
        return registryPort;
    }
    
    public void setRegistryPort(Integer value) {
        registryPort = value;
    }
    
    public String getMbeanServerDefaultDomain() {
        return mbeanServerDefaultDomain;
    }
    
    public void setMbeanServerDefaultDomain(String value) {
        mbeanServerDefaultDomain = value;
    }
    
    public String getMbeanObjectDomainName() {
        return mbeanObjectDomainName;
    }
    
    public void setMbeanObjectDomainName(String value) {
        mbeanObjectDomainName = value;
    }
    
    public String getServiceUrlPath() {
        return serviceUrlPath;
    }
    
    public void setServiceUrlPath(String value) {
        serviceUrlPath = value;
    }

    public Boolean isCreateConnector() {
        return createConnector;
    }
    
    public void setCreateConnector(Boolean value) {
        createConnector = value !=  null ? value : Boolean.FALSE;
    }

    public Boolean isUsePlatformMBeanServer() {
        return usePlatformMBeanServer;
    }
    
    public void setUsePlatformMBeanServer(Boolean value) {
        usePlatformMBeanServer = value !=  null ? value : Boolean.FALSE;
    }

    public Boolean isDisabled() {
        return disabled;
    }
    
    public void setDisabled(Boolean value) {
        disabled = value != null ? value : Boolean.FALSE;
    }
}
