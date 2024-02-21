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
package org.apache.camel.main;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.util.OrderedProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainHelperTest {

    private MainHelper helper = new MainHelper();

    @Test
    public void testAddComponentEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_COMPONENT_" });
        env.put("CAMEL_COMPONENT_AWS2_S3_ACCESS_KEY", "mysecretkey");
        Properties prop = new OrderedProperties();
        helper.addComponentEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(1, prop.size());
        Assertions.assertEquals("mysecretkey", prop.getProperty("camel.component.aws2-s3.access-key"));
    }

    @Test
    public void testAddDataFormatEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_DATAFORMAT_" });
        env.put("CAMEL_DATAFORMAT_BASE64_LINE_LENGTH", "64");
        env.put("CAMEL_DATAFORMAT_JACKSONXML_PRETTYPRINT", "true");
        Properties prop = new OrderedProperties();
        helper.addDataFormatEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(2, prop.size());
        Assertions.assertEquals("64", prop.getProperty("camel.dataformat.base64.line-length"));
        Assertions.assertEquals("true", prop.getProperty("camel.dataformat.jacksonxml.prettyprint"));
    }

    @Test
    public void testAddLanguageEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_LANGUAGE_" });
        env.put("CAMEL_LANGUAGE_JOOR_PRE_COMPILE", "false");
        Properties prop = new OrderedProperties();
        helper.addLanguageEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(1, prop.size());
        Assertions.assertEquals("false", prop.getProperty("camel.language.joor.pre-compile"));
    }

    @Test
    public void testAddCustomComponentEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_COMPONENT_" });
        env.put("CAMEL_COMPONENT_AWS2_S3_ACCESS_KEY", "mysecretkey");
        env.put("CAMEL_COMPONENT_FOO_VERBOSE", "true");
        env.put("CAMEL_COMPONENT_FOO_PRETTY_PRINT", "false");
        Properties prop = new OrderedProperties();
        helper.addComponentEnvVariables(env, prop, false);

        Assertions.assertEquals(2, env.size());
        Assertions.assertEquals(1, prop.size());
        Assertions.assertEquals("mysecretkey", prop.getProperty("camel.component.aws2-s3.access-key"));

        helper.addComponentEnvVariables(env, prop, true);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(3, prop.size());
        Assertions.assertEquals("mysecretkey", prop.getProperty("camel.component.aws2-s3.access-key"));
        Assertions.assertEquals("true", prop.getProperty("camel.component.foo.verbose"));
        Assertions.assertEquals("false", prop.getProperty("camel.component.foo.pretty-print"));
    }

}
