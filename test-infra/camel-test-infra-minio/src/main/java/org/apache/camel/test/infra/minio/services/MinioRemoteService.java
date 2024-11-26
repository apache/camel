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
package org.apache.camel.test.infra.minio.services;

import org.apache.camel.test.infra.minio.common.MinioProperties;

public class MinioRemoteService implements MinioService {

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @Override
    public String secretKey() {
        return System.getProperty(MinioProperties.SECRET_KEY);
    }

    @Override
    public String accessKey() {
        return System.getProperty(MinioProperties.ACCESS_KEY);
    }

    @Override
    public int port() {
        String port = System.getProperty(MinioProperties.SERVICE_PORT);

        if (port == null) {
            return MinioProperties.DEFAULT_SERVICE_PORT;
        }

        return Integer.valueOf(port);
    }

    @Override
    public String host() {
        return System.getProperty(MinioProperties.SERVICE_HOST);
    }
}
