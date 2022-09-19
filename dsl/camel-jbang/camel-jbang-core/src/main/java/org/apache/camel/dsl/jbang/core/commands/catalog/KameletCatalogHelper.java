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
package org.apache.camel.dsl.jbang.core.commands.catalog;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.support.ObjectHelper;

public final class KameletCatalogHelper {

    private KameletCatalogHelper() {
    }

    public static KameletModel createModel(Object kamelet) throws Exception {
        KameletModel km = new KameletModel();
        km.name = getName(kamelet);
        km.type = getType(kamelet);
        km.supportLevel = getSupportLevel(kamelet);
        km.description = getDescription(kamelet);
        return km;
    }

    private static String getName(Object kamelet) throws Exception {
        Method m = kamelet.getClass().getMethod("getMetadata");
        Object meta = ObjectHelper.invokeMethod(m, kamelet);
        m = meta.getClass().getMethod("getName");
        return (String) ObjectHelper.invokeMethod(m, meta);
    }

    public static Map<String, Object> loadKamelets(String version) throws Exception {
        ClassLoader cl = createClassLoader();
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setClassLoader(cl);
        downloader.start();
        downloader.downloadDependency("org.apache.camel.kamelets", "camel-kamelets-catalog", version);

        Thread.currentThread().setContextClassLoader(cl);
        Class<?> clazz = cl.loadClass("org.apache.camel.kamelets.catalog.KameletsCatalog");
        Object catalog = clazz.getDeclaredConstructor().newInstance();
        Method m = clazz.getMethod("getKamelets");
        return (Map<String, Object>) ObjectHelper.invokeMethod(m, catalog);
    }

    public static KameletModel loadKameletModel(String name, String version) throws Exception {
        Map<String, Object> kamelets = loadKamelets(version);
        if (kamelets != null) {
            Object k = kamelets.get(name);
            if (k != null) {
                return createModel(k);
            }
        }
        return null;
    }

    private static ClassLoader createClassLoader() {
        ClassLoader parentCL = CatalogKamelet.class.getClassLoader();
        return new DependencyDownloaderClassLoader(parentCL);
    }

    private static String getType(Object kamelet) throws Exception {
        Method m = kamelet.getClass().getMethod("getMetadata");
        Object meta = ObjectHelper.invokeMethod(m, kamelet);
        m = meta.getClass().getMethod("getLabels");
        Map labels = (Map) ObjectHelper.invokeMethod(m, meta);
        if (labels != null) {
            return (String) labels.get("camel.apache.org/kamelet.type");
        }
        return null;
    }

    private static String getSupportLevel(Object kamelet) throws Exception {
        Method m = kamelet.getClass().getMethod("getMetadata");
        Object meta = ObjectHelper.invokeMethod(m, kamelet);
        m = meta.getClass().getMethod("getAnnotations");
        Map anns = (Map) ObjectHelper.invokeMethod(m, meta);
        if (anns != null) {
            return (String) anns.get("camel.apache.org/kamelet.support.level");
        }
        return null;
    }

    private static String getDescription(Object kamelet) throws Exception {
        Method m = kamelet.getClass().getMethod("getSpec");
        Object spec = ObjectHelper.invokeMethod(m, kamelet);
        m = spec.getClass().getMethod("getDefinition");
        Object def = ObjectHelper.invokeMethod(m, spec);
        m = def.getClass().getMethod("getDescription");
        return (String) ObjectHelper.invokeMethod(m, def);
    }
}
