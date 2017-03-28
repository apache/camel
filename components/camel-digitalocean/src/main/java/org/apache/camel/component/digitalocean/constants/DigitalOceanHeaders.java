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
package org.apache.camel.component.digitalocean.constants;

public interface DigitalOceanHeaders {

    String OPERATION = "CamelDigitalOceanOperation";
    String ID = "CamelDigitalOceanId";
    String TYPE = "CamelDigitalOceanType";
    String NAME = "CamelDigitalOceanName";
    String NEW_NAME = "CamelDigitalOceanNewName";
    String NAMES = "CamelDigitalOceanNames";
    String REGION = "CamelDigitalOceanRegion";
    String DESCRIPTION = "CamelDigitalOceanDescription";

    String DROPLET_SIZE = "CamelDigitalOceanDropletSize";
    String DROPLET_IMAGE = "CamelDigitalOceanDropletImage";
    String DROPLET_KEYS = "CamelDigitalOceanDropletSSHKeys";
    String DROPLET_ENABLE_BACKUPS = "CamelDigitalOceanDropletEnableBackups";
    String DROPLET_ENABLE_IPV6 = "CamelDigitalOceanDropletEnableIpv6";
    String DROPLET_ENABLE_PRIVATE_NETWORKING = "CamelDigitalOceanDropletEnablePrivateNetworking";
    String DROPLET_USER_DATA = "CamelDigitalOceanDropletUserData";
    String DROPLET_VOLUMES = "CamelDigitalOceanDropletVolumes";
    String DROPLET_TAGS = "CamelDigitalOceanDropletTags";

    String DROPLET_ID = "CamelDigitalOceanDropletId";
    String IMAGE_ID = "CamelDigitalOceanImageId";
    String KERNEL_ID = "CamelDigitalOceanKernelId";
    String VOLUME_NAME = "CamelDigitalOceanVolumeName";
    String VOLUME_SIZE_GIGABYTES = "CamelDigitalOceanVolumeSizeGigabytes";

    String FLOATING_IP_ADDRESS = "CamelDigitalOceanFloatingIPAddress";

    String KEY_FINGERPRINT = "CamelDigitalOceanKeyFingerprint";
    String KEY_PUBLIC_KEY = "CamelDigitalOceanKeyPublicKey";
}
