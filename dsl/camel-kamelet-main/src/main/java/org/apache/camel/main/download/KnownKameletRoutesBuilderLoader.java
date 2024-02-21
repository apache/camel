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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.yaml.KameletRoutesBuilderLoader;
import org.apache.camel.main.util.SuggestSimilarHelper;
import org.apache.camel.main.util.VersionHelper;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ReflectionHelper;

public class KnownKameletRoutesBuilderLoader extends KameletRoutesBuilderLoader {

    private String kameletsVersion;

    public KnownKameletRoutesBuilderLoader(String kameletsVersion) {
        this.kameletsVersion = kameletsVersion;
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        if (!resource.exists()) {
            String loc = resource.getLocation();
            String name = FileUtil.onlyName(loc, false);

            List<String> suggestion = SuggestSimilarHelper.didYouMean(findKameletNames(), name);
            if (suggestion != null && !suggestion.isEmpty()) {
                String s = String.join(", ", suggestion);
                throw new IllegalArgumentException("Cannot find Kamelet with name: " + name + ". Did you mean: " + s);
            }
        }

        return super.doLoadRouteBuilder(resource);
    }

    private List<String> findKameletNames() {
        // download kamelet catalog for the correct version
        if (kameletsVersion == null) {
            kameletsVersion = VersionHelper.extractKameletsVersion();
        }

        try {
            // dynamic download kamelets-catalog that has the known names
            MavenDependencyDownloader downloader = getCamelContext().hasService(MavenDependencyDownloader.class);
            if (!downloader.alreadyOnClasspath("org.apache.camel.kamelets", "camel-kamelets-catalog", kameletsVersion)) {
                downloader.downloadDependency("org.apache.camel.kamelets", "camel-kamelets-catalog", kameletsVersion);
            }
            // create an instance of the catalog and invoke its getKameletsName method
            Class<?> clazz = getCamelContext().getClassResolver()
                    .resolveClass("org.apache.camel.kamelets.catalog.KameletsCatalog");
            if (clazz != null) {
                Object catalog = getCamelContext().getInjector().newInstance(clazz);
                Method m = ReflectionHelper.findMethod(clazz, "getKameletsName");
                return (List<String>) ObjectHelper.invokeMethod(m, catalog);
            }
        } catch (Exception e) {
            // ignore
        }

        return Collections.emptyList();
    }
}
