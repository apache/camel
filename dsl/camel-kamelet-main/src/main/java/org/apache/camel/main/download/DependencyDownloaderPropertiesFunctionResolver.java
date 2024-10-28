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

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.DefaultPropertiesFunctionResolver;
import org.apache.camel.spi.PropertiesFunction;

/**
 * Auto downloaded needed JARs when resolving properties functions.
 */
public class DependencyDownloaderPropertiesFunctionResolver extends DefaultPropertiesFunctionResolver {

    private final boolean export;

    public DependencyDownloaderPropertiesFunctionResolver(CamelContext camelContext, boolean export) {
        super();
        setCamelContext(camelContext);
        this.export = export;
    }

    @Override
    public PropertiesFunction resolvePropertiesFunction(String name) {
        DependencyDownloader downloader = getCamelContext().hasService(DependencyDownloader.class);
        if ("base64".equals(name)) {
            if (downloader != null && !downloader.alreadyOnClasspath("org.apache.camel", "camel-base64",
                    getCamelContext().getVersion())) {
                downloader.downloadDependency("org.apache.camel", "camel-base64",
                        getCamelContext().getVersion());
            }
        }
        if ("configmap".equals(name) || "secret".equals(name)) {
            if (downloader != null && !downloader.alreadyOnClasspath("org.apache.camel", "camel-kubernetes",
                    getCamelContext().getVersion())) {
                downloader.downloadDependency("org.apache.camel", "camel-kubernetes",
                        getCamelContext().getVersion());
            }
        }
        if ("aws".equals(name)) {
            if (downloader != null && !downloader.alreadyOnClasspath("org.apache.camel", "camel-aws-secrets-manager",
                    getCamelContext().getVersion())) {
                downloader.downloadDependency("org.apache.camel", "camel-aws-secrets-manager",
                        getCamelContext().getVersion());
            }
        }
        if ("azure".equals(name)) {
            if (downloader != null && !downloader.alreadyOnClasspath("org.apache.camel", "camel-azure-key-vault",
                    getCamelContext().getVersion())) {
                downloader.downloadDependency("org.apache.camel", "camel-azure-key-vault",
                        getCamelContext().getVersion());
            }
        }
        if ("gcp".equals(name)) {
            if (downloader != null && !downloader.alreadyOnClasspath("org.apache.camel", "camel-google-secret-manager",
                    getCamelContext().getVersion())) {
                downloader.downloadDependency("org.apache.camel", "camel-google-secret-manager",
                        getCamelContext().getVersion());
            }
        }
        if ("hashicorp".equals(name)) {
            if (downloader != null && !downloader.alreadyOnClasspath("org.apache.camel", "camel-hashicorp-vault",
                    getCamelContext().getVersion())) {
                downloader.downloadDependency("org.apache.camel", "camel-hashicorp-vault",
                        getCamelContext().getVersion());
            }
        }
        PropertiesFunction answer = super.resolvePropertiesFunction(name);
        if (answer != null && export) {
            answer = new ExportPropertiesFunction(answer);
        }
        return answer;
    }

    private static class ExportPropertiesFunction implements PropertiesFunction {

        private final PropertiesFunction delegate;

        private ExportPropertiesFunction(PropertiesFunction delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean lookupFirst(String remainder) {
            try {
                return delegate.lookupFirst(remainder);
            } catch (Exception e) {
                // ignore
            }
            return false;
        }

        @Override
        public String apply(String remainder) {
            try {
                return delegate.apply(remainder);
            } catch (Exception e) {
                // ignore
            }
            return null;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public boolean optional(String remainder) {
            return true;
        }
    }

}
