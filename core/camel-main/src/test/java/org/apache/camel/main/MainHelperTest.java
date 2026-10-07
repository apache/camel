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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.util.OrderedProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MainHelperTest {

    private final MainHelper helper = new MainHelper();

    @Test
    void testAddComponentEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_COMPONENT_" });
        env.put("CAMEL_COMPONENT_AWS2_S3_ACCESS_KEY", "mysecretkey");
        Properties prop = new OrderedProperties();
        helper.addComponentEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(1, prop.size());
        Assertions.assertEquals("mysecretkey", prop.getProperty("camel.component.aws2-s3.access-key"));
    }

    @Test
    void testAddDataFormatEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_DATAFORMAT_" });
        env.put("CAMEL_DATAFORMAT_BASE64_LINE_LENGTH", "64");
        env.put("CAMEL_DATAFORMAT_JACKSONXML_PRETTYPRINT", "true");
        Properties prop = new OrderedProperties();
        helper.addDataFormatEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(2, prop.size());
        Assertions.assertEquals("64", prop.getProperty("camel.dataformat.base64.line-length"));
        Assertions.assertEquals("true", prop.getProperty("camel.dataformat.jacksonXml.prettyprint"));
    }

    @Test
    void testAddLanguageEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_LANGUAGE_" });
        env.put("CAMEL_LANGUAGE_JAVA_PRE_COMPILE", "false");
        Properties prop = new OrderedProperties();
        helper.addLanguageEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(1, prop.size());
        Assertions.assertEquals("false", prop.getProperty("camel.language.java.pre-compile"));
    }

    @Test
    void testAddCustomComponentEnvVariables() {
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

    /**
     * Verifies that overlapping component names (e.g. NETTY vs NETTY_HTTP, SJMS vs SJMS2, FILE vs FILE_WATCH) are
     * resolved by longest-match so that each variable is mapped to the correct component.
     */
    @Test
    void testLongestMatchComponentEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_COMPONENT_" });
        // netty-http must not be mapped to netty
        env.put("CAMEL_COMPONENT_NETTY_HTTP_MUTE_EXCEPTION", "true");
        // sjms2 must not be mapped to sjms
        env.put("CAMEL_COMPONENT_SJMS2_RECOVERY_INTERVAL", "2000");
        // file-watch must not be mapped to file
        env.put("CAMEL_COMPONENT_FILE_WATCH_QUEUE_SIZE", "5000");
        // netty itself (no suffix clash)
        env.put("CAMEL_COMPONENT_NETTY_RECEIVE_BUFFER_SIZE", "65536");

        Properties prop = new OrderedProperties();
        helper.addComponentEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(4, prop.size());
        Assertions.assertEquals("true", prop.getProperty("camel.component.netty-http.mute-exception"));
        Assertions.assertEquals("2000", prop.getProperty("camel.component.sjms2.recovery-interval"));
        Assertions.assertEquals("5000", prop.getProperty("camel.component.file-watch.queue-size"));
        Assertions.assertEquals("65536", prop.getProperty("camel.component.netty.receive-buffer-size"));
    }

    /**
     * Verifies that overlapping language names (e.g. JS vs JSONPATH) are resolved by longest-match.
     */
    @Test
    void testLongestMatchLanguageEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_LANGUAGE_" });
        // jsonpath must not be mapped to js
        env.put("CAMEL_LANGUAGE_JSONPATH_SUPPRESS_EXCEPTIONS", "true");

        Properties prop = new OrderedProperties();
        helper.addLanguageEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(1, prop.size());
        Assertions.assertEquals("true", prop.getProperty("camel.language.jsonpath.suppress-exceptions"));
    }

    /**
     * Verifies that overlapping dataformat names (e.g. AVRO vs AVROJACKSON) are resolved by longest-match.
     */
    @Test
    void testLongestMatchDataFormatEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_DATAFORMAT_" });
        // avroJackson must not be mapped to avro
        env.put("CAMEL_DATAFORMAT_AVROJACKSON_AUTO_DISCOVER_OBJECT_MAPPER", "true");
        // avro itself
        env.put("CAMEL_DATAFORMAT_AVRO_INSTANCE_CLASS_NAME", "com.example.MySchema");

        Properties prop = new OrderedProperties();
        helper.addDataFormatEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(2, prop.size());
        Assertions.assertEquals("true", prop.getProperty("camel.dataformat.avroJackson.auto-discover-object-mapper"));
        Assertions.assertEquals("com.example.MySchema", prop.getProperty("camel.dataformat.avro.instance-class-name"));
    }

    /**
     * Verifies that the custom pass only takes its own prefix: CAMEL_LANGUAGE_* and CAMEL_DATAFORMAT_* leftovers must
     * not bleed into the component bucket.
     */
    @Test
    void testCustomPassPrefixGuard() {
        // Simulate: catalog pass consumed aws2-s3 but left unknowns for other types
        Map<String, String> env = new HashMap<>();
        env.put("CAMEL_LANGUAGE_FOO_BAR", "x");
        env.put("CAMEL_DATAFORMAT_FOO_BAR", "y");

        Properties prop = new OrderedProperties();
        // component custom pass: must not consume CAMEL_LANGUAGE_* or CAMEL_DATAFORMAT_*
        helper.addComponentEnvVariables(env, prop, true);
        Assertions.assertEquals(2, env.size(),
                "CAMEL_LANGUAGE_* and CAMEL_DATAFORMAT_* must be left in env after component custom pass");
        Assertions.assertEquals(0, prop.size(), "No camel.component.* must be produced from non-component prefixes");

        // dataformat custom pass: must consume CAMEL_DATAFORMAT_* only
        helper.addDataFormatEnvVariables(env, prop, true);
        Assertions.assertEquals(1, env.size(), "CAMEL_LANGUAGE_* must remain after dataformat custom pass");
        Assertions.assertEquals("y", prop.getProperty("camel.dataformat.foo.bar"));

        // language custom pass: must consume CAMEL_LANGUAGE_* only
        helper.addLanguageEnvVariables(env, prop, true);
        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals("x", prop.getProperty("camel.language.foo.bar"));
    }

    /**
     * Verifies that camelCase language names (e.g. exchangeProperty) are preserved through the catalog map rather than
     * lowercased.
     */
    @Test
    void testCamelCaseLanguageEnvVariables() {
        Map<String, String> env = MainHelper.filterEnvVariables(new String[] { "CAMEL_LANGUAGE_" });
        // exchangeProperty is a camelCase language name — must not become "exchangeproperty"
        env.put("CAMEL_LANGUAGE_EXCHANGEPROPERTY_TRIM", "false");

        Properties prop = new OrderedProperties();
        helper.addLanguageEnvVariables(env, prop, false);

        Assertions.assertEquals(0, env.size());
        Assertions.assertEquals(1, prop.size());
        Assertions.assertEquals("false", prop.getProperty("camel.language.exchangeProperty.trim"));
    }

}
