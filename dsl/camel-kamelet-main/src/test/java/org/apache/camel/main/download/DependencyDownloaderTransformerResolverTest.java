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
package org.apache.camel.main.download;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.tooling.model.TransformerModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DependencyDownloaderTransformerResolverTest {

    @Test
    void stubTransformerShouldHaveNameSet() {
        SimpleCamelContext context = new SimpleCamelContext();
        // transformers require "transformer:" prefix to be stubbed
        DependencyDownloaderTransformerResolver resolver
                = new DependencyDownloaderTransformerResolver(context, "transformer:*", true);

        // use a name not in the catalog to avoid triggering artifact download
        String transformerName = "test-stub:application-json";
        TransformerKey key = new TransformerKey(transformerName);
        Transformer transformer = resolver.resolve(key, context);

        assertNotNull(transformer);
        assertEquals(transformerName, transformer.getName(),
                "StubTransformer must have the requested name set");

        TransformerKey resultKey = TransformerKey.createFrom(transformer);
        assertNotNull(resultKey);
    }

    @Test
    void wildcardPatternShouldNotStubTransformers() {
        SimpleCamelContext context = new SimpleCamelContext();
        // stub pattern "*" should NOT stub transformers (only components)
        DependencyDownloaderTransformerResolver resolver
                = new DependencyDownloaderTransformerResolver(context, "*", true);

        // transformer is not stubbed so resolve will throw for unknown names
        String transformerName = "test-stub:application-json";
        TransformerKey key = new TransformerKey(transformerName);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(key, context));
    }

    @Test
    void catalogLookupShouldNormalizeColonSeparatedNames() {
        DefaultCamelCatalog catalog = new DefaultCamelCatalog();
        DependencyDownloaderTransformerResolver resolver
                = new DependencyDownloaderTransformerResolver(new SimpleCamelContext(), "*", true);

        // transformer names use colon format (e.g., aws2-ddb:application-json)
        // but the catalog stores them in dash format (aws2-ddb-application-json)
        String colonName = "aws2-ddb:application-json";
        String normalizedName = resolver.normalize(new TransformerKey(colonName));

        TransformerModel model = catalog.transformerModel(normalizedName);
        assertNotNull(model, "Catalog should find transformer using normalized name: " + normalizedName);
        assertNotNull(model.getArtifactId(), "Transformer model should have artifactId");
    }
}
