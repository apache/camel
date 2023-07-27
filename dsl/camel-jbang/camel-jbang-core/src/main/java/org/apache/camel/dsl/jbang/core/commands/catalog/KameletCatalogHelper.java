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

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.StringHelper;

public final class KameletCatalogHelper {

    private KameletCatalogHelper() {
    }

    public static List<String> findKameletNames(String version) throws Exception {
        Map<String, Object> kamelets = loadKamelets(version);
        return new ArrayList<>(kamelets.keySet());
    }

    public static KameletModel createModel(Object kamelet, boolean all) throws Exception {
        KameletModel km = new KameletModel();
        km.name = getName(kamelet);
        km.type = getType(kamelet);
        km.supportLevel = getSupportLevel(kamelet);
        km.description = getDescription(kamelet);
        if (all) {
            km.dependencies = getDependencies(kamelet);

            Map<String, Object> props = getProperties(kamelet);
            if (props != null) {
                km.properties = new LinkedHashMap<>();
                for (var es : props.entrySet()) {
                    KameletOptionModel om = createOptionModel(es.getKey(), es.getValue());
                    km.properties.put(om.name, om);
                }
                // some options are required
                List<String> required = getRequired(kamelet);
                if (required != null && !required.isEmpty()) {
                    for (var r : required) {
                        KameletOptionModel om = km.properties.get(r);
                        if (om != null) {
                            om.required = true;
                        }
                    }
                }
            }
        }
        return km;
    }

    private static KameletOptionModel createOptionModel(String name, Object prop) throws Exception {
        KameletOptionModel om = new KameletOptionModel();
        om.name = name;
        om.description = getPropertyDescription(prop);
        om.type = getPropertyType(prop);
        om.defaultValue = getPropertyDefaultValue(prop);
        om.example = getPropertyExample(prop);
        om.enumValues = getPropertyEnum(prop);
        return om;
    }

    private static List<String> getRequired(Object kamelet) throws Exception {
        Method m = kamelet.getClass().getMethod("getSpec");
        Object spec = ObjectHelper.invokeMethod(m, kamelet);
        m = spec.getClass().getMethod("getDefinition");
        Object def = ObjectHelper.invokeMethod(m, spec);
        m = def.getClass().getMethod("getRequired");
        return (List<String>) ObjectHelper.invokeMethod(m, def);
    }

    private static String getPropertyDescription(Object prop) throws Exception {
        Method m = prop.getClass().getMethod("getDescription");
        return (String) ObjectHelper.invokeMethod(m, prop);
    }

    private static String getPropertyType(Object prop) throws Exception {
        Method m = prop.getClass().getMethod("getType");
        return (String) ObjectHelper.invokeMethod(m, prop);
    }

    private static String getPropertyExample(Object prop) throws Exception {
        Method m = prop.getClass().getMethod("getExample");
        Object en = ObjectHelper.invokeMethod(m, prop);
        if (en != null) {
            String t = en.toString();
            return StringHelper.removeLeadingAndEndingQuotes(t);
        }
        return null;
    }

    private static String getPropertyDefaultValue(Object prop) throws Exception {
        Method m = prop.getClass().getMethod("getDefault");
        Object dn = ObjectHelper.invokeMethod(m, prop);
        if (dn != null) {
            String t = dn.toString();
            return StringHelper.removeLeadingAndEndingQuotes(t);
        }
        return null;
    }

    private static List<String> getPropertyEnum(Object prop) throws Exception {
        List<String> answer = new ArrayList<>();
        Method m = prop.getClass().getMethod("getEnum");
        List<Object> list = (List<Object>) ObjectHelper.invokeMethod(m, prop);
        if (list != null && !list.isEmpty()) {
            for (var en : list) {
                String t = en.toString();
                t = StringHelper.removeLeadingAndEndingQuotes(t);
                answer.add(t);
            }
        }
        return answer.isEmpty() ? null : answer;
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

    public static InputStream loadKameletYamlSchema(String name, String version) throws Exception {
        ClassLoader cl = createClassLoader();
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setClassLoader(cl);
        downloader.start();
        downloader.downloadDependency("org.apache.camel.kamelets", "camel-kamelets-catalog", version);
        Thread.currentThread().setContextClassLoader(cl);
        return cl.getResourceAsStream("kamelets/" + name + ".kamelet.yaml");
    }

    public static KameletModel loadKameletModel(String name, String version) throws Exception {
        Map<String, Object> kamelets = loadKamelets(version);
        if (kamelets != null) {
            Object k = kamelets.get(name);
            if (k != null) {
                return createModel(k, true);
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
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) ObjectHelper.invokeMethod(m, meta);
        if (labels != null) {
            return labels.get("camel.apache.org/kamelet.type");
        }
        return null;
    }

    private static String getSupportLevel(Object kamelet) throws Exception {
        Method m = kamelet.getClass().getMethod("getMetadata");
        Object meta = ObjectHelper.invokeMethod(m, kamelet);
        m = meta.getClass().getMethod("getAnnotations");
        @SuppressWarnings("unchecked")
        Map<String, String> anns = (Map<String, String>) ObjectHelper.invokeMethod(m, meta);
        if (anns != null) {
            return anns.get("camel.apache.org/kamelet.support.level");
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

    private static Map<String, Object> getProperties(Object kamelet) throws Exception {
        Method m = kamelet.getClass().getMethod("getSpec");
        Object spec = ObjectHelper.invokeMethod(m, kamelet);
        m = spec.getClass().getMethod("getDefinition");
        Object def = ObjectHelper.invokeMethod(m, spec);
        m = def.getClass().getMethod("getProperties");
        return (Map<String, Object>) ObjectHelper.invokeMethod(m, def);
    }

    private static List<String> getDependencies(Object kamelet) throws Exception {
        List<String> answer = new ArrayList<>();
        Method m = kamelet.getClass().getMethod("getSpec");
        Object spec = ObjectHelper.invokeMethod(m, kamelet);
        m = spec.getClass().getMethod("getDependencies");
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) ObjectHelper.invokeMethod(m, spec);
        if (list != null && !list.isEmpty()) {
            for (var en : list) {
                String t = en.toString();
                t = StringHelper.removeLeadingAndEndingQuotes(t);
                answer.add(t);
            }
        }
        return answer.isEmpty() ? null : answer;
    }

}
