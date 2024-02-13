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
package org.apache.camel.component.qdrant;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.qdrant.services.QdrantService;
import org.apache.camel.test.infra.qdrant.services.QdrantServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class QdrantTestSupport extends CamelTestSupport {
    @RegisterExtension
    static QdrantService QDRANT = QdrantServiceFactory.createSingletonService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        QdrantComponent component = context.getComponent(Qdrant.SCHEME, QdrantComponent.class);
        component.getConfiguration().setHost(QDRANT.getGrpcHost());
        component.getConfiguration().setPort(QDRANT.getGrpcPort());
        component.getConfiguration().setTls(false);

        return context;
    }
}
