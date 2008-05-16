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
    @XmlAttribute(required = false)
    private Integer connectorPort;
    @XmlAttribute(required = false)
    private String jmxDomainName;
    @XmlAttribute(required = false)
    private String connectorPath;
    @XmlAttribute(required = false)
    private Boolean createConnector;
    @XmlAttribute(required = false)
    private Boolean usePlatformMBeanServer;

    public void setConnectorPort(Integer port) {
        connectorPort = port;
    }

    public Integer getConnectorPort() {
        return connectorPort;
    }

    public void setJmxDomainName(String name) {
        jmxDomainName = name;
    }

    public String getJmxDomainName() {
        return jmxDomainName;
    }

    public void setConnectorPath(String path) {
        connectorPath = path;
    }

    public String getConnectorPath() {
        return connectorPath;
    }

    public void setCreateConnector(Boolean flag) {
        createConnector = flag;
    }

    public Boolean isCreateConnector() {
        return createConnector;
    }

    public void setUsePlatformMBeanServer(Boolean flag) {
        usePlatformMBeanServer = flag;
    }

    public Boolean isUsePlatformMBeanServer() {
        return usePlatformMBeanServer;
    }
}
