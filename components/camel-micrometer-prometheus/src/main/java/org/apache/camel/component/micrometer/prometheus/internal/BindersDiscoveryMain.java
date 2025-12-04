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

package org.apache.camel.component.micrometer.prometheus.internal;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.camel.component.micrometer.prometheus.BindersHelper;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.util.StringHelper;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

/**
 * WARNING: NOT INTENDED FOR PUBLIC USAGE!
 *
 * This class is an internal class used for a DEVELOPER ONLY CLI required to expose a list of available meters.
 */
public class BindersDiscoveryMain {

    private static final String JANDEX_INDEX = "META-INF/micrometer-binder-index.dat";

    public static void main(String[] args) throws Exception {
        Set<String> answer = new TreeSet<>();

        Index index = BindersHelper.readJandexIndex(new DefaultClassResolver());
        if (index == null) {
            System.out.println("Cannot read " + JANDEX_INDEX + " with list of known MeterBinder classes");
        } else {
            DotName dn = DotName.createSimple(MeterBinder.class);
            Set<ClassInfo> classes = index.getAllKnownImplementors(dn);
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
}
