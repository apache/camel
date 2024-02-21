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
package org.apache.camel.component.kubernetes;

public final class KubernetesCategory {

    public static final String NAMESPACES = "namespaces";

    public static final String SERVICES = "services";

    public static final String REPLICATION_CONTROLLERS = "replicationControllers";

    public static final String PODS = "pods";

    public static final String PERSISTENT_VOLUMES = "persistentVolumes";

    public static final String PERSISTENT_VOLUMES_CLAIMS = "persistentVolumesClaims";

    public static final String SECRETS = "secrets";

    public static final String RESOURCES_QUOTA = "resourcesQuota";

    public static final String SERVICE_ACCOUNTS = "serviceAccounts";

    public static final String NODES = "nodes";

    public static final String CONFIGMAPS = "configMaps";

    public static final String BUILDS = "builds";

    public static final String BUILD_CONFIGS = "buildConfigs";

    private KubernetesCategory() {

    }
}
