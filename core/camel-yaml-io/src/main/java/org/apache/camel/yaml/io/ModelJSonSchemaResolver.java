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
package org.apache.camel.yaml.io;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.catalog.JSonSchemaResolver;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * To load model json metadata
 */
class ModelJSonSchemaResolver implements JSonSchemaResolver {

    @Override
    public void setClassLoader(ClassLoader classLoader) {
        // not in use
    }

    @Override
    public String getComponentJSonSchema(String name) {
        throw new UnsupportedOperationException("Only getModelJSonSchema is in use");
    }

    @Override
    public String getDataFormatJSonSchema(String name) {
        throw new UnsupportedOperationException("Only getModelJSonSchema is in use");
    }

    @Override
    public String getLanguageJSonSchema(String name) {
        throw new UnsupportedOperationException("Only getModelJSonSchema is in use");
    }

    @Override
    public String getTransformerJSonSchema(String name) {
        throw new UnsupportedOperationException("Only getModelJSonSchema is in use");
    }

    @Override
    public String getOtherJSonSchema(String name) {
        throw new UnsupportedOperationException("Only getModelJSonSchema is in use");
    }

    @Override
    public String getModelJSonSchema(String name) {
        try {
            String[] subPackages = new String[] {
                    "", "cloud/", "config/", "dataformat/", "errorhandler/", "language/", "loadbalancer/", "rest/",
                    "transformer/", "validator/" };
            for (String sub : subPackages) {
                String path = CamelContextHelper.MODEL_DOCUMENTATION_PREFIX + sub + name + ".json";
                String inputStream = doLoadResource(path);
                if (inputStream != null) {
                    return inputStream;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    public String getMainJsonSchema() {
        throw new UnsupportedOperationException("Only getModelJSonSchema is in use");
    }

    private static String doLoadResource(String path) throws IOException {
        InputStream inputStream = ObjectHelper.loadResourceAsStream(path, Thread.currentThread().getContextClassLoader());
        if (inputStream != null) {
            try {
                return IOHelper.loadText(inputStream);
            } finally {
                IOHelper.close(inputStream);
            }
        }
        return null;
    }

}
