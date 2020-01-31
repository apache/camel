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
package org.apache.camel.itest.karaf;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class CamelMiloTest extends BaseKarafTest {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        Assume.assumeTrue("Requires java 9+", isJavaVersionSatisfied(9));
    }

    @Test
    public void testClient() throws Exception {
        testComponent("milo", "milo-client");
    }
    @Test
    public void testServer() throws Exception {
        testComponent("milo", "milo-server");
    }

    /**
     * Return true, if java version (defined by method getRequiredJavaVersion()) is satisfied.
     * Works for java versions 9+
     */
    boolean isJavaVersionSatisfied(int requiredVersion) {
        String version = System.getProperty("java.version");
        if (!version.startsWith("1.")) {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
            if (Integer.parseInt(version) >= requiredVersion) {
                return true;
            }
        }
        return false;
    }
}
