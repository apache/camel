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
package org.apache.camel.component.micrometer.prometheus;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BindersHelper {

    private static final Logger LOG = LoggerFactory.getLogger(BindersHelper.class);
    private static final String JANDEX_INDEX = "META-INF/micrometer-binder-index.dat";

    private BindersHelper() {
    }

    public static void main(String[] args) throws Exception {
        Set<String> answer = new TreeSet<>();

        Index index = readJandexIndex(new DefaultClassResolver());
        if (index == null) {
            System.out.println("Cannot read " + JANDEX_INDEX + " with list of known MeterBinder classes");
        } else {
            DotName dn = DotName.createSimple(MeterBinder.class);
            List<ClassInfo> classes = index.getKnownDirectImplementors(dn);
            for (ClassInfo info : classes) {
                boolean deprecated = info.hasAnnotation(Deprecated.class);
                if (deprecated) {
                    // skip deprecated
                    continue;
                }
                boolean abs = Modifier.isAbstract(info.flags());
                if (abs) {
                    // skip abstract
                    continue;
                }
                boolean noArg = info.hasNoArgsConstructor();
                if (!noArg) {
                    // skip binders that need extra configuration
                    continue;
                }
                String name = info.name().local();
                if (name.endsWith("Metrics")) {
                    name = name.substring(0, name.length() - 7);
                }
                name = StringHelper.camelCaseToDash(name);
                answer.add(name);
            }
        }

        StringJoiner sj = new StringJoiner(", ");
        answer.forEach(sj::add);
        System.out.println(sj);
    }

    public static List<String> discoverBinders(ClassResolver classResolver, String names) throws IOException {
        List<String> answer = new ArrayList<>();

        LOG.debug("Loading {}", JANDEX_INDEX);
        Index index = readJandexIndex(classResolver);
        if (index == null) {
            LOG.warn("Cannot read {} with list of known MeterBinder classes", JANDEX_INDEX);
        } else {
            DotName dn = DotName.createSimple(MeterBinder.class);
            List<ClassInfo> classes = index.getKnownDirectImplementors(dn);
            LOG.debug("Found {} MeterBinder classes from {}", classes.size(), JANDEX_INDEX);

            for (String binder : names.split(",")) {
                binder = binder.trim();
                binder = StringHelper.dashToCamelCase(binder);
                binder = binder.toLowerCase();

                final String target = binder;
                Optional<ClassInfo> found = classes.stream()
                        // use naming convention with and without metrics
                        .filter(c -> c.name().local().toLowerCase().equals(target)
                                || c.name().local().toLowerCase().equals(target + "metrics"))
                        .findFirst();

                if (found.isPresent()) {
                    String fqn = found.get().name().toString();
                    answer.add(fqn);
                }
            }
        }

        return answer;
    }

    public static List<MeterBinder> loadBinders(CamelContext camelContext, List<String> binders) {
        List<MeterBinder> answer = new ArrayList<>();

        for (String fqn : binders) {
            LOG.debug("Creating MeterBinder: {}", fqn);
            try {
                Class<MeterBinder> clazz = camelContext.getClassResolver().resolveClass(fqn, MeterBinder.class);
                MeterBinder mb = camelContext.getInjector().newInstance(clazz);
                if (mb != null) {
                    answer.add(mb);
                }
            } catch (Exception e) {
                LOG.warn("Error creating MeterBinder: {} due to: {}. This exception is ignored.", fqn, e.getMessage(),
                        e);
            }
        }

        return answer;
    }

    public static Index readJandexIndex(ClassResolver classResolver) throws IOException {
        InputStream is = classResolver.loadResourceAsStream("META-INF/micrometer-binder-index.dat");
        try {
            if (is != null) {
                IndexReader reader = new IndexReader(is);
                return reader.read();
            }
        } finally {
            IOHelper.close(is);
        }
        return null;
    }

}
