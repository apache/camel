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
package org.apache.camel.maven.dsl.yaml.support;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.maven.project.MavenProject;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

public final class IndexerSupport {
    private IndexerSupport() {
    }

    public static ClassLoader getClassLoader(MavenProject project) {
        if (project == null) {
            return IndexerSupport.class.getClassLoader();
        }

        try {
            List<String> elements = new ArrayList<>(project.getCompileClasspathElements());
            URL[] urls = new URL[elements.size()];
            for (int i = 0; i < elements.size(); ++i) {
                urls[i] = new File(elements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, IndexerSupport.class.getClassLoader());
        } catch (Exception e) {
            return IndexerSupport.class.getClassLoader();
        }
    }

    public static IndexView get(MavenProject project) {
        try {
            ClassLoader classLoader = getClassLoader(project);
            Enumeration<URL> elements = classLoader.getResources("META-INF/jandex.idx");
            List<IndexView> allIndex = new ArrayList<>();
            Set<URL> locations = new HashSet<>();

            while (elements.hasMoreElements()) {
                URL url = elements.nextElement();
                if (locations.add(url)) {
                    try (InputStream is = url.openStream()) {
                        allIndex.add(new IndexReader(is).read());
                    }
                }
            }

            return CompositeIndex.create(allIndex);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<FieldInfo> fields(IndexView view, ClassInfo type) {
        return fields(view, type, fi -> true);
    }

    public static List<FieldInfo> fields(IndexView view, ClassInfo type, Predicate<FieldInfo> filter) {
        List<FieldInfo> answer = new ArrayList<>();

        for (ClassInfo current = type; current != null;) {
            for (FieldInfo fieldInfo : current.fields()) {
                if (filter.test(fieldInfo)) {
                    answer.add(fieldInfo);
                }
            }

            Type superType = current.superClassType();
            if (superType == null) {
                break;
            }

            current = view.getClassByName(superType.name());
        }

        return answer;
    }

    public static List<MethodInfo> methods(IndexView view, ClassInfo type) {
        return methods(view, type, mi -> true);
    }

    public static List<MethodInfo> methods(IndexView view, ClassInfo type, Predicate<MethodInfo> filter) {
        List<MethodInfo> answer = new ArrayList<>();

        for (ClassInfo current = type; current != null;) {
            for (MethodInfo methodInfo : current.methods()) {
                if (filter.test(methodInfo)) {
                    answer.add(methodInfo);
                }
            }

            Type superType = current.superClassType();
            if (superType == null) {
                break;
            }

            current = view.getClassByName(superType.name());
        }

        return answer;
    }
}
