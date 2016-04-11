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
package org.apache.camel.management;

/**
 * This module contains jmx related system property key constants.
 *
 * @version 
 */
public final class JmxSystemPropertyKeys {

    // disable jmx
    public static final String DISABLED = "org.apache.camel.jmx.disabled";

    // jmx (rmi registry) port
    public static final String REGISTRY_PORT = "org.apache.camel.jmx.rmiConnector.registryPort";
    
    // jmx (rmi server connection) port
    public static final String CONNECTOR_PORT = "org.apache.camel.jmx.rmiConnector.connectorPort";

    // jmx domain name
    public static final String DOMAIN = "org.apache.camel.jmx.mbeanServerDefaultDomain";
    
    // the domain name for the camel mbeans
    public static final String MBEAN_DOMAIN = "org.apache.camel.jmx.mbeanObjectDomainName";

    // JMX service URL path 
    public static final String SERVICE_URL_PATH = "org.apache.camel.jmx.serviceUrlPath";
    
    // A flag that indicates whether the agent should be created
    public static final String CREATE_CONNECTOR = "org.apache.camel.jmx.createRmiConnector";
    
    // use jvm platform mbean server flag
    public static final String USE_PLATFORM_MBS = "org.apache.camel.jmx.usePlatformMBeanServer";

    // whether all processors or only processors with a custom id given should be registered
    public static final String ONLY_REGISTER_PROCESSOR_WITH_CUSTOM_ID = "org.apache.camel.jmx.onlyRegisterProcessorWithCustomId";

    // whether to enable gathering load statistics in the background
    public static final String LOAD_STATISTICS_ENABLED = "org.apache.camel.jmx.loadStatisticsEnabled";

    // whether to enable gathering endpoint runtime statistics
    public static final String ENDPOINT_RUNTIME_STATISTICS_ENABLED = "org.apache.camel.jmx.endpointRuntimeStatisticsEnabled";

    // the level of statistics enabled
    public static final String STATISTICS_LEVEL = "org.apache.camel.jmx.statisticsLevel";

    // whether to register always
    public static final String REGISTER_ALWAYS = "org.apache.camel.jmx.registerAlways";

    // whether to register when starting new routes
    public static final String REGISTER_NEW_ROUTES = "org.apache.camel.jmx.registerNewRoutes";

    // Whether to remove detected sensitive information (such as passwords) from MBean names and attributes.
    public static final String MASK = "org.apache.camel.jmx.mask";

    // Whether to include host name in MBean names
    public static final String INCLUDE_HOST_NAME = "org.apache.camel.jmx.includeHostName";

    // To configure the default management name pattern using a JVM system property
    public static final String MANAGEMENT_NAME_PATTERN = "org.apache.camel.jmx.managementNamePattern";
    
    // flag to enable host ip address instead of host name 
    public static final String USE_HOST_IP_ADDRESS = "org.apache.camel.jmx.useHostIPAddress";


    private JmxSystemPropertyKeys() {
        // not instantiated
    }

}
