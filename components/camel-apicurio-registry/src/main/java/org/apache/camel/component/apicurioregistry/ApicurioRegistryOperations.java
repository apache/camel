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
package org.apache.camel.component.apicurioregistry;

/**
 * Producer operations supported by the Apicurio Registry component.
 */
public enum ApicurioRegistryOperations {
    /**
     * Create a new artifact (with its first version) in a group.
     */
    createArtifact,
    /**
     * Add a new version to an existing artifact.
     */
    updateArtifact,
    /**
     * Get the content of the latest version of an artifact.
     */
    getArtifact,
    /**
     * Get the content of a specific version of an artifact.
     */
    getArtifactVersion,
    /**
     * List the versions of an artifact.
     */
    listArtifactVersions,
    /**
     * Delete an artifact (all its versions) from a group.
     */
    deleteArtifact,
    /**
     * Delete a specific version of an artifact.
     */
    deleteArtifactVersion
}
