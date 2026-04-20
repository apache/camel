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
package org.apache.camel.test.infra.postgres.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.postgres.common.PostgresProperties;

@InfraService(service = PostgresInfraService.class,
              description = "PostgreSQL with pgvector extension for vector similarity search",
              serviceAlias = { "postgres-vector", "pgvector" })
public class PostgresVectorLocalContainerInfraService extends PostgresLocalContainerInfraService {

    public static final String DEFAULT_POSTGRES_VECTOR_CONTAINER
            = LocalPropertyResolver.getProperty(PostgresLocalContainerInfraService.class,
                    PostgresProperties.POSTGRES_VECTOR_CONTAINER);

    public PostgresVectorLocalContainerInfraService() {
        super(DEFAULT_POSTGRES_VECTOR_CONTAINER);
    }

    public PostgresVectorLocalContainerInfraService(String imageName) {
        super(imageName);
    }
}
