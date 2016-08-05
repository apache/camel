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
package org.apache.camel.component.kubernetes;

public interface KubernetesCategory {

    String NAMESPACES = "namespaces";
    
    String SERVICES = "services";
    
    String REPLICATION_CONTROLLERS = "replicationControllers";
    
    String PODS = "pods";
    
    String PERSISTENT_VOLUMES = "persistentVolumes";
    
    String PERSISTENT_VOLUMES_CLAIMS = "persistentVolumesClaims";
    
    String SECRETS = "secrets";
    
    String RESOURCES_QUOTA = "resourcesQuota";
    
    String SERVICE_ACCOUNTS = "serviceAccounts";
    
    String NODES = "nodes";
    
    String CONFIGMAPS = "configMaps";
    
    String BUILDS = "builds";
    
    String BUILD_CONFIGS = "buildConfigs";
}
