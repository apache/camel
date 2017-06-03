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
package org.apache.camel.catalog.nexus;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.camel.catalog.CamelCatalog;

public class LocalFileComponentCatalogNexusRepository extends ComponentCatalogNexusRepository {

    private Runnable onAddComponent;

    public Runnable getOnAddComponent() {
        return onAddComponent;
    }

    public void setOnAddComponent(Runnable onAddComponent) {
        this.onAddComponent = onAddComponent;
    }

    @Override
    protected URL createNexusUrl() throws MalformedURLException {
        File file = new File("target/test-classes/nexus-sample-component-result.xml");
        return new URL("file:" + file.getAbsolutePath());
    }

    @Override
    protected String createArtifactURL(NexusArtifactDto dto) {
        // load from local file instead
        return "file:target/localrepo/" + dto.getArtifactId() + "-" + dto.getVersion() + ".jar";
    }

    @Override
    protected void addComponent(NexusArtifactDto dto, CamelCatalog camelCatalog, String scheme, String javaType, String json) {
        super.addComponent(dto, camelCatalog, scheme, javaType, json);

        if (onAddComponent != null) {
            onAddComponent.run();
        }
    }
}
