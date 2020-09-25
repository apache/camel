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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.catalog.impl.CamelCatalogEndpointUriAssembler;
import org.apache.camel.spi.EndpointUriAssembler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AssembleResolverTest extends ContextTestSupport {

    @Test
    public void testAssembleResolver() throws Exception {
        // will use source code generated
        EndpointUriAssembler assembler = context.adapt(ExtendedCamelContext.class).getAssemblerResolver()
                .resolveAssembler("log", context);
        Assertions.assertNotNull(assembler);
        // TODO: check classname

        // will then use fallback that is catalog based
        assembler = context.adapt(ExtendedCamelContext.class).getAssemblerResolver()
                .resolveAssembler("unknown", context);
        Assertions.assertNotNull(assembler);
        boolean catalog = assembler instanceof CamelCatalogEndpointUriAssembler;
        Assertions.assertTrue(catalog);
    }
}
