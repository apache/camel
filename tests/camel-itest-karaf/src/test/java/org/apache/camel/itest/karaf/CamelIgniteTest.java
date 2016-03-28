/**
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
package org.apache.camel.itest.karaf;

import java.net.URI;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class CamelIgniteTest extends AbstractFeatureTest {

    public static final String COMPONENT = extractName(CamelIgniteTest.class);

    @Test
    public void test() throws Exception {
        // install ignite first
        String version = "1.5.0.final";
        LOG.info("Using Apache Ignite version: {}", version);
        URI url = new URI("mvn:org.apache.ignite/ignite-osgi-karaf/" + version + "/xml/features");

        featuresService.addRepository(url);
        featuresService.installFeature("ignite-core");
        featuresService.installFeature("ignite-camel");

        testComponent(COMPONENT);
    }

}