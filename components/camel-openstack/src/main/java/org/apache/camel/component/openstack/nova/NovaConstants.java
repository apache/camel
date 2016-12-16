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
package org.apache.camel.component.openstack.nova;

import org.apache.camel.component.openstack.common.OpenstackConstants;

public final class NovaConstants extends OpenstackConstants {
    public static final String NOVA_SUBSYSTEM_FLAVORS = "flavors";
    public static final String NOVA_SUBSYSTEM_SERVERS = "servers";
    public static final String NOVA_SUBSYSTEM_KEYPAIRS = "keypairs";

    public static final String FLAVOR_ID = "FlavorId";

    //flavor
    public static final String RAM = "RAM";
    public static final String VCPU = "VCPU";
    public static final String DISK = "disk";
    public static final String SWAP = "swap";
    public static final String RXTXFACTOR = "rxtxFactor";

    //server
    public static final String ADMIN_PASSWORD = "AdminPassword";
    public static final String IMAGE_ID = "ImageId";
    public static final String KEYPAIR_NAME = "KeypairName";
    public static final String NETWORK = "NetworkId";

    //server
    public static final String CREATE_SNAPSHOT = "createSnapshot";
    public static final String ACTION = "action";

    private NovaConstants() { }
}
