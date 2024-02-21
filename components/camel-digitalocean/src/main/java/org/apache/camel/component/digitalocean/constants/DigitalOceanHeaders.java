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
package org.apache.camel.component.digitalocean.constants;

import org.apache.camel.spi.Metadata;

public interface DigitalOceanHeaders {
    @Metadata(description = "The operation to perform",
              javaType = "org.apache.camel.component.digitalocean.constants.DigitalOceanOperations")
    String OPERATION = "CamelDigitalOceanOperation";
    @Metadata(description = "The id", javaType = "Integer or String")
    String ID = "CamelDigitalOceanId";
    @Metadata(description = "The type", javaType = "org.apache.camel.component.digitalocean.constants.DigitalOceanImageTypes")
    String TYPE = "CamelDigitalOceanType";
    @Metadata(description = "The name", javaType = "String")
    String NAME = "CamelDigitalOceanName";
    String NEW_NAME = "CamelDigitalOceanNewName";
    @Metadata(description = "The names of the droplet", javaType = "List<String>")
    String NAMES = "CamelDigitalOceanNames";
    @Metadata(description = "The code name of the region aka DigitalOcean data centers", javaType = "String")
    String REGION = "CamelDigitalOceanRegion";
    @Metadata(description = "The description", javaType = "String")
    String DESCRIPTION = "CamelDigitalOceanDescription";
    @Metadata(description = "The size of the droplet", javaType = "String")
    String DROPLET_SIZE = "CamelDigitalOceanDropletSize";
    @Metadata(description = "The image of the droplet", javaType = "String")
    String DROPLET_IMAGE = "CamelDigitalOceanDropletImage";
    @Metadata(description = "The keys of the droplet", javaType = "List<String>")
    String DROPLET_KEYS = "CamelDigitalOceanDropletSSHKeys";
    @Metadata(description = "The flag to enable backups", javaType = "Boolean")
    String DROPLET_ENABLE_BACKUPS = "CamelDigitalOceanDropletEnableBackups";
    @Metadata(description = "The flag to enable ipv6", javaType = "Boolean")
    String DROPLET_ENABLE_IPV6 = "CamelDigitalOceanDropletEnableIpv6";
    @Metadata(description = "The flag to enable private networking", javaType = "Boolean")
    String DROPLET_ENABLE_PRIVATE_NETWORKING = "CamelDigitalOceanDropletEnablePrivateNetworking";
    @Metadata(description = "The user data of the droplet", javaType = "String")
    String DROPLET_USER_DATA = "CamelDigitalOceanDropletUserData";
    @Metadata(description = "The volumes' identifier of the droplet", javaType = "List<String>")
    String DROPLET_VOLUMES = "CamelDigitalOceanDropletVolumes";
    @Metadata(description = "The tags of the droplet", javaType = "List<String>")
    String DROPLET_TAGS = "CamelDigitalOceanDropletTags";
    @Metadata(description = "The droplet identifier", javaType = "Integer")
    String DROPLET_ID = "CamelDigitalOceanDropletId";
    @Metadata(description = "The id of the DigitalOcean public image or your private image", javaType = "Integer")
    String IMAGE_ID = "CamelDigitalOceanImageId";
    @Metadata(description = "The kernel id to be changed for droplet", javaType = "Integer")
    String KERNEL_ID = "CamelDigitalOceanKernelId";
    @Metadata(description = "The name of the volume", javaType = "String")
    String VOLUME_NAME = "CamelDigitalOceanVolumeName";
    @Metadata(description = "The size value in GB", javaType = "Integer or Double")
    String VOLUME_SIZE_GIGABYTES = "CamelDigitalOceanVolumeSizeGigabytes";
    @Metadata(description = "The floating IP address", javaType = "String")
    String FLOATING_IP_ADDRESS = "CamelDigitalOceanFloatingIPAddress";
    @Metadata(description = "The SSH key fingerprint", javaType = "String")
    String KEY_FINGERPRINT = "CamelDigitalOceanKeyFingerprint";
    @Metadata(description = "The public key", javaType = "String")
    String KEY_PUBLIC_KEY = "CamelDigitalOceanKeyPublicKey";
}
