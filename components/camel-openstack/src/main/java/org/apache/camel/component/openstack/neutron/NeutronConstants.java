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
package org.apache.camel.component.openstack.neutron;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.spi.Metadata;

public final class NeutronConstants extends OpenstackConstants {

    public static final String NEUTRON_NETWORK_SUBSYSTEM = "networks";
    public static final String NEUTRON_SUBNETS_SYSTEM = "subnets";
    public static final String NEUTRON_PORT_SYSTEM = "ports";
    public static final String NEUTRON_ROUTER_SYSTEM = "routers";
    @Metadata(label = "network port router", description = "Tenant ID.", javaType = "String")
    public static final String TENANT_ID = "CamelOpenstackNeutronTenantId";
    @Metadata(label = "subnet port", description = "Network ID.", javaType = "String")
    public static final String NETWORK_ID = "CamelOpenstackNeutronNetworkId";

    //network
    @Metadata(label = "network", description = "AdminStateUp header.", javaType = "Boolean")
    public static final String ADMIN_STATE_UP = "CamelOpenstackNeutronAdminStateUp";
    @Metadata(label = "network", description = "Network type.", javaType = "org.openstack4j.model.network.NetworkType")
    public static final String NETWORK_TYPE = "CamelOpenstackNeutronNetworkType";
    @Metadata(label = "network", description = "Physical network.", javaType = "String")
    public static final String PHYSICAL_NETWORK = "CamelOpenstackNeutronPhysicalNetwork";
    @Metadata(label = "network", description = "Segment ID.", javaType = "String")
    public static final String SEGMENT_ID = "CamelOpenstackNeutronSegmentId";
    @Metadata(label = "network", description = "Is shared.", javaType = "Boolean")
    public static final String IS_SHARED = "CamelOpenstackNeutronIsShared";
    @Metadata(label = "network", description = "Is router external.", javaType = "Boolean")
    public static final String IS_ROUTER_EXTERNAL = "CamelOpenstackNeutronIsRouterExternal";

    //subnet
    @Metadata(label = "subnet", description = "Enable DHCP.", javaType = "Boolean")
    public static final String ENABLE_DHCP = "CamelOpenstackNeutronEnableDhcp";
    @Metadata(label = "subnet", description = "Gateway.", javaType = "String")
    public static final String GATEWAY = "CamelOpenstackNeutronGateway";
    @Metadata(label = "subnet", description = "IP version.", javaType = "org.openstack4j.model.network.IPVersionType")
    public static final String IP_VERSION = "CamelOpenstackNeutronIpVersion";
    @Metadata(label = "subnet", description = "The cidr representing the IP range for this subnet, based on IP version.",
              javaType = "String")
    public static final String CIDR = "CamelOpenstackNeutronCidr";
    @Metadata(label = "subnet", description = "The allocation pool.",
              javaType = "org.openstack4j.openstack.networking.domain.NeutronPool")
    public static final String SUBNET_POOL = "CamelOpenstackNeutronSubnetPools";

    //port
    @Metadata(label = "port", description = "Device ID.", javaType = "String")
    public static final String DEVICE_ID = "CamelOpenstackNeutronDeviceId";
    @Metadata(label = "port", description = "MAC address.", javaType = "String")
    public static final String MAC_ADDRESS = "CamelOpenstackNeutronMacAddress";

    //router
    @Metadata(label = "router", description = "Router ID.", javaType = "String")
    public static final String ROUTER_ID = "CamelOpenstackNeutronRouterId";
    @Metadata(label = "router subnet", description = "Subnet ID.", javaType = "String")
    public static final String SUBNET_ID = "CamelOpenstackNeutronSubnetId";
    @Metadata(label = "port router", description = "Port ID.", javaType = "String")
    public static final String PORT_ID = "CamelOpenstackNeutronPortId";
    @Metadata(label = "router", description = "Interface type.", javaType = "org.openstack4j.model.network.AttachInterfaceType")
    public static final String ITERFACE_TYPE = "CamelOpenstackNeutronInterfaceType";

    public static final String ATTACH_INTERFACE = "attachInterface";
    public static final String DETACH_INTERFACE = "detachInterface";

    private NeutronConstants() {
    }

}
