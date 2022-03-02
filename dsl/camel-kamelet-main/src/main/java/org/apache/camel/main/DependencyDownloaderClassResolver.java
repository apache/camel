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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.util.ObjectHelper;

final class DependencyDownloaderClassResolver extends DefaultClassResolver {

    private final KnownDependenciesResolver knownDependenciesResolver;

    public DependencyDownloaderClassResolver(CamelContext camelContext, KnownDependenciesResolver knownDependenciesResolver) {
        super(camelContext);
        this.knownDependenciesResolver = knownDependenciesResolver;
    }

    @Override
    protected Class<?> loadClass(String name, ClassLoader loader) {
        Class<?> answer = null;
        try {
            answer = ObjectHelper.loadClass(name, loader);
        } catch (Exception e) {
            // class cannot be loaded, likely need maven GAV
        }

        if (answer == null) {
            // okay maybe the class is from a known GAV that we can download first and then load the class
            MavenGav gav = knownDependenciesResolver.mavenGavForClass(name);
            if (gav != null) {
                if (!DownloaderHelper.alreadyOnClasspath(getCamelContext(), gav.getArtifactId(), gav.getVersion())) {
                    DownloaderHelper.downloadDependency(getCamelContext(), gav.getGroupId(), gav.getArtifactId(),
                            gav.getVersion());
                }
                try {
                    answer = ObjectHelper.loadClass(name, loader);
                } catch (Exception e) {
                    // class cannot be loaded, likely need maven GAV
                }
            }
        }

        return answer;
    }

}
