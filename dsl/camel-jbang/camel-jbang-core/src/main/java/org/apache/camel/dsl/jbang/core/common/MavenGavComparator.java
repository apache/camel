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
package org.apache.camel.dsl.jbang.core.common;

import java.util.Comparator;

import org.apache.camel.tooling.maven.MavenGav;

public class MavenGavComparator implements Comparator<MavenGav> {

    @Override
    public int compare(MavenGav o1, MavenGav o2) {
        int r1 = rankGroupId(o1);
        int r2 = rankGroupId(o2);

        if (r1 > r2) {
            return -1;
        } else if (r2 > r1) {
            return 1;
        } else {
            return o1.toString().compareTo(o2.toString());
        }
    }

    int rankGroupId(MavenGav o1) {
        String g1 = o1.getGroupId();
        if ("org.springframework.boot".equals(g1)) {
            return 30;
        } else if ("io.quarkus".equals(g1)) {
            return 30;
        } else if ("org.apache.camel.quarkus".equals(g1)) {
            String a1 = o1.getArtifactId();
            // main/core/engine first
            if ("camel-quarkus-core".equals(a1)) {
                return 21;
            }
            return 20;
        } else if ("org.apache.camel.springboot".equals(g1)) {
            String a1 = o1.getArtifactId();
            // main/core/engine first
            if ("camel-spring-boot-engine-starter".equals(a1)) {
                return 21;
            }
            return 20;
        } else if ("org.apache.camel".equals(g1)) {
            String a1 = o1.getArtifactId();
            // main/core/engine first
            if ("camel-main".equals(a1)) {
                return 11;
            }
            return 10;
        } else if ("org.apache.camel.kamelets".equals(g1)) {
            return 5;
        } else {
            return 0;
        }
    }
}
