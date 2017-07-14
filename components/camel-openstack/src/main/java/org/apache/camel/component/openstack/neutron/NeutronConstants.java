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
package org.apache.camel.component.openstack.neutron;

import org.apache.camel.component.openstack.common.OpenstackConstants;

public final class NeutronConstants extends OpenstackConstants {

    public static final String NEUTRON_NETWORK_SUBSYSTEM = "networks";
    public static final String NEUTRON_SUBNETS_SYSTEM = "subnets";
    public static final String NEUTRON_PORT_SYSTEM = "ports";
    public static final String NEUTRON_ROUTER_SYSTEM = "routers";
    public static final String TENANT_ID = "tenantId";
    public static final String NETWORK_ID = "networkId";

    //network
    public static final String ADMIN_STATE_UP = "adminStateUp";
    public static final String NETWORK_TYPE = "networkType";
    public static final String PHYSICAL_NETWORK = "physicalNetwork";
    public static final String SEGMENT_ID = "segmentId";
    public static final String IS_SHARED = "isShared";
    public static final String IS_ROUTER_EXTERNAL = "isRouterExternal";

    //subnet
    public static final String ENABLE_DHCP = "enableDHCP";
    public static final String GATEWAY = "gateway";
    public static final String IP_VERSION = "ipVersion";
    public static final String CIDR = "cidr";
    public static final String SUBNET_POOL = "subnetPools";

    //port
    public static final String DEVICE_ID = "deviceId";
    public static final String MAC_ADDRESS = "macAddress";

    //router
    public static final String ROUTER_ID = "routerId";
    public static final String SUBNET_ID = "subnetId";
    public static final String PORT_ID = "portId";
    public static final String ITERFACE_TYPE = "interfaceType";

    public static final String ATTACH_INTERFACE = "attachInterface";
    public static final String DETACH_INTERFACE = "detachInterface";

    private NeutronConstants() { }

}
