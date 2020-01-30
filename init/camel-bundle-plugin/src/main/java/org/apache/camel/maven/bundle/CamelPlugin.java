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
package org.apache.camel.maven.bundle;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import aQute.bnd.annotation.plugin.BndPlugin;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Resource;
import aQute.bnd.service.AnalyzerPlugin;

@BndPlugin(name = "camel")
public class CamelPlugin implements AnalyzerPlugin {

    @Override
    public boolean analyzeJar(Analyzer analyzer) {
        Jar jar = analyzer.getJar();
        Map<String, Map<String, Resource>> dir = jar.getDirectories();

        Stream<String> components = dir.getOrDefault("META-INF/services/org/apache/camel/component", Collections.emptyMap()).keySet().stream()
            .map(s -> s.substring(s.lastIndexOf('/') + 1)).map(s -> "osgi.service;effective:=active;objectClass=\"org.apache.camel.spi.ComponentResolver\";component=" + s);
        Stream<String> languages = dir.getOrDefault("META-INF/services/org/apache/camel/language", Collections.emptyMap()).keySet().stream()
            .map(s -> s.substring(s.lastIndexOf('/') + 1)).map(s -> "osgi.service;effective:=active;objectClass=\"org.apache.camel.spi.LanguageResolver\";language=" + s);
        Stream<String> dataformats = dir.getOrDefault("META-INF/services/org/apache/camel/dataformat", Collections.emptyMap()).keySet().stream()
            .map(s -> s.substring(s.lastIndexOf('/') + 1)).map(s -> "osgi.service;effective:=active;objectClass=\"org.apache.camel.spi.DataformatResolver\";dataformat=" + s);
        String header = analyzer.getProperty("Provide-Capability");

        header = Stream.concat(header != null && !header.isEmpty() ? Stream.of(header) : Stream.empty(), Stream.concat(components, Stream.concat(languages, dataformats)))
            .collect(Collectors.joining(","));

        analyzer.setProperty("Provide-Capability", header);

        return false;
    }

}
