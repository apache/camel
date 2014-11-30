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
package org.apache.camel.component.chunk;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the component that manages {@link ChunksEndpoint}.
 *
 * @version 
 */
public class ChunkComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ChunkEndpoint endpoint = new ChunkEndpoint(uri, this, remaining);
        String encoding = getAndRemoveParameter(parameters, "encoding", String.class);
        if (ObjectHelper.isNotEmpty(encoding)) {
            endpoint.setEncoding(encoding);
        }
        String themesFolder = getAndRemoveParameter(parameters, "themesFolder", String.class);
        if (ObjectHelper.isNotEmpty(themesFolder)) {
            endpoint.setThemeFolder(themesFolder);
        }
        String themeSubfolder = getAndRemoveParameter(parameters, "themeSubfolder", String.class);
        if (ObjectHelper.isNotEmpty(themeSubfolder)) {
            endpoint.setThemeSubfolder(themeSubfolder);
        }
        String themeLayer = getAndRemoveParameter(parameters, "themeLayer", String.class);
        if (ObjectHelper.isNotEmpty(themeLayer)) {
            endpoint.setThemeLayer(themeLayer);
        }
        String extension = getAndRemoveParameter(parameters, "extension", String.class);
        if (ObjectHelper.isNotEmpty(extension)) {
            endpoint.setExtension(extension);
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
