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
package org.apache.camel.test.infra.qdrant.services;

import java.net.http.HttpResponse;
import java.util.Map;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.apache.camel.test.infra.qdrant.common.QdrantProperties;

public final class QdrantServiceFactory {
    private QdrantServiceFactory() {

    }

    public static class SingletonQdrantService extends SingletonService<QdrantService> implements QdrantService {
        public SingletonQdrantService(QdrantService service, String name) {
            super(service, name);
        }

        @Override
        public String getHttpHost() {
            return getService().getHttpHost();
        }

        @Override
        public int getHttpPort() {
            return getService().getHttpPort();
        }

        @Override
        public String getGrpcHost() {
            return getService().getGrpcHost();
        }

        @Override
        public int getGrpcPort() {
            return getService().getGrpcPort();
        }

        @Override
        public HttpResponse<byte[]> put(String path, Map<Object, Object> body) throws Exception {
            return getService().put(path, body);
        }
    }

    public static SimpleTestServiceBuilder<QdrantService> builder() {
        return new SimpleTestServiceBuilder<>(QdrantProperties.INFRA_TYPE);
    }

    public static QdrantService createService() {
        return builder()
                .addLocalMapping(QdrantLocalContainerService::new)
                .addRemoteMapping(QdrantRemoteService::new)
                .build();
    }

    public static QdrantService createSingletonService() {
        return builder()
                .addLocalMapping(
                        () -> new SingletonQdrantService(new QdrantLocalContainerService(), QdrantProperties.INFRA_TYPE))
                .build();
    }
}
