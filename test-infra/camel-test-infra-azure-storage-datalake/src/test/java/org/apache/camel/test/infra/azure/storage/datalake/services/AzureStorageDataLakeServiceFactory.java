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

package org.apache.camel.test.infra.azure.storage.datalake.services;

import org.apache.camel.test.infra.azure.common.services.AzureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AzureStorageDataLakeServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(AzureStorageDataLakeServiceFactory.class);

    private AzureStorageDataLakeServiceFactory() {

    }

    public static AzureService createAzureService() {
        String instanceType = System.getProperty("azure.instance.type");

        if (instanceType == null || "remote".equals(instanceType)) {
            return new AzureStorageDataLakeRemoteService();
        }

        // add support for azurite in future

        LOG.error("Azure instance supported at present: 'remote'");
        throw new UnsupportedOperationException(String.format("Invalid Azure instance type: %s", instanceType));
    }
}
