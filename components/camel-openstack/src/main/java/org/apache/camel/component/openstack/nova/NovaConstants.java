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
package org.apache.camel.component.openstack.nova;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.spi.Metadata;

public final class NovaConstants extends OpenstackConstants {
    public static final String NOVA_SUBSYSTEM_FLAVORS = "flavors";
    public static final String NOVA_SUBSYSTEM_SERVERS = "servers";
    public static final String NOVA_SUBSYSTEM_KEYPAIRS = "keypairs";

    @Metadata(label = "flavor server", description = "ID of the flavor.", javaType = "String")
    public static final String FLAVOR_ID = "FlavorId";

    //flavor
    @Metadata(label = "flavor", description = "Size of RAM.", javaType = "Integer")
    public static final String RAM = "RAM";
    @Metadata(label = "flavor", description = "The number of flavor VCPU.", javaType = "Integer")
    public static final String VCPU = "VCPU";
    @Metadata(label = "flavor", description = "Size of disk.", javaType = "Integer")
    public static final String DISK = "disk";
    @Metadata(label = "flavor", description = "Size of swap.", javaType = "Integer")
    public static final String SWAP = "swap";
    @Metadata(label = "flavor", description = "Rxtx Factor.", javaType = "Integer")
    public static final String RXTXFACTOR = "rxtxFactor";

    //server
    @Metadata(label = "server", description = "Admin password of the new server.", javaType = "String")
    public static final String ADMIN_PASSWORD = "AdminPassword";
    @Metadata(label = "server", description = "The Image ID.", javaType = "String")
    public static final String IMAGE_ID = "ImageId";
    @Metadata(label = "server", description = "The Keypair name.", javaType = "String")
    public static final String KEYPAIR_NAME = "KeypairName";
    @Metadata(label = "server", description = "The list of networks (by id).", javaType = "List<String>")
    public static final String NETWORK = "NetworkId";

    //server
    public static final String CREATE_SNAPSHOT = "createSnapshot";
    @Metadata(label = "server", description = "An action to perform.", javaType = "org.openstack4j.model.compute.Action")
    public static final String ACTION = "action";

    private NovaConstants() {
    }
}
