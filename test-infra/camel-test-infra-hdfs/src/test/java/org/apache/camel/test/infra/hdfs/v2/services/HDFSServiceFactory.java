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

package org.apache.camel.test.infra.hdfs.v2.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HDFSServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(HDFSServiceFactory.class);

    private HDFSServiceFactory() {

    }

    public static HDFSService createService() {
        String instanceType = System.getProperty("hdfs.instance.type");

        if (instanceType == null || instanceType.equals("local-hdfs-container")) {
            return new ContainerLocalHDFSService();
        }

        if (instanceType.equals("remote")) {
            return new RemoteHDFSService();
        }

        LOG.error("Invalid HDFS instance type: {}. Must be either 'remote' or 'local-hdfs-container",
                instanceType);
        throw new UnsupportedOperationException(String.format("Invalid HDFS instance type: %s", instanceType));

    }
}
