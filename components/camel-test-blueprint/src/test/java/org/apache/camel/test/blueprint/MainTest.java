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
package org.apache.camel.test.blueprint;


import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.aries.util.io.IOUtils;
import org.apache.camel.ProducerTemplate;
import org.junit.Test;
import org.ops4j.pax.swissbox.tinybundles.core.TinyBundle;
import org.ops4j.pax.swissbox.tinybundles.core.TinyBundles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class MainTest {

    private static final String SYMBOLIC_NAME = "testMainWithoutIncludingTestBundle";

    @Test
    public void testMyMain() throws Exception {
        Main main = new Main();
        main.setBundleName("MyMainBundle");
        // as we run this test without packing ourselves as bundle, then include ourselves
        main.setIncludeSelfAsBundle(true);
        // setup the blueprint file here
        main.setDescriptors("org/apache/camel/test/blueprint/main-loadfile.xml");
        // set the configAdmin persistent id
        main.setConfigAdminPid("stuff");
        // set the configAdmin persistent file name
        main.setConfigAdminFileName("src/test/resources/etc/stuff.cfg");
        main.start();
        
        ProducerTemplate template = main.getCamelTemplate();
        assertNotNull("We should get the template here", template);
        
        String result = template.requestBody("direct:start", "hello", String.class);
        assertEquals("Get a wrong response", "Bye hello", result);
        main.stop();
    }

    @Test
    public void testMainWithoutIncludingTestBundle() throws Exception {
        TinyBundle bundle = TinyBundles.newBundle();
        bundle.add("OSGI-INF/blueprint/camel.xml", getClass().getResourceAsStream("main-loadfile.xml"));
        bundle.set("Manifest-Version", "2")
                .set("Bundle-ManifestVersion", "2")
                .set("Bundle-SymbolicName", SYMBOLIC_NAME)
                .set("Bundle-Version", "1.0.0");
        File tb = File.createTempFile(SYMBOLIC_NAME + "-", ".jar", new File("target"));
        FileOutputStream out = new FileOutputStream(tb);
        IOUtils.copy(bundle.build(), out);
        out.close();

        // simulate `camel:run` which is run after packaging the artifact, so a "bundle" (location with
        // META-INF/MANIFEST.MF) is detected in target/classes
        URLClassLoader loader = new URLClassLoader(new URL[] {tb.toURI().toURL()}, getClass().getClassLoader());

        Main main = new Main();
        main.setLoader(loader);
        // bundle name will be used as filter for blueprint container filter
        main.setBundleName(SYMBOLIC_NAME);
        // don't include test bundle (which is what `mvn camel:run` actually does)
        main.setIncludeSelfAsBundle(false);
        // don't setup the blueprint file here - it'll be picked up from a bundle on classpath
        //main.setDescriptors("none!");
        // set the configAdmin persistent id
        main.setConfigAdminPid("stuff");
        // set the configAdmin persistent file name
        main.setConfigAdminFileName("src/test/resources/etc/stuff.cfg");
        main.doStart();

        ProducerTemplate template = main.getCamelTemplate();
        assertNotNull("We should get the template here", template);

        String result = template.requestBody("direct:start", "hello", String.class);
        assertEquals("Get a wrong response", "Bye hello", result);
        main.stop();
    }

}
