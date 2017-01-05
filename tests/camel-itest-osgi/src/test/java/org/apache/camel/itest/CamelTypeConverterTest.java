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
package org.apache.camel.itest;

import java.net.URL;
import java.util.Locale;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.itest.typeconverter.MyConverter;
import org.apache.camel.test.karaf.AbstractFeatureTest;
import org.apache.camel.test.karaf.CamelKarafTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.InnerClassStrategy;
import org.ops4j.pax.tinybundles.core.TinyBundle;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

@RunWith(PaxExam.class)
public class CamelTypeConverterTest extends AbstractFeatureTest {

    @Test
    public void testTypeConverterInSameBundleAsCamelRoute() throws Exception {
        // install the camel blueprint xml file and the Camel converter we use in this test
        TinyBundle bundle = TinyBundles.bundle();
        // install blueprint
        URL blueprintUrl = ObjectHelper.loadResourceAsURL("org/apache/camel/itest/CamelJacksonFallbackConverterTest.xml", CamelJacksonFallbackConverterTest.class.getClassLoader());
        String name = "CamelJacksonFallbackConverterTest";
        bundle.add("OSGI-INF/blueprint/blueprint-" + name.toLowerCase(Locale.ENGLISH) + ".xml", blueprintUrl);
        // install converter
        URL converterDeclaration = ObjectHelper.loadResourceAsURL("org/apache/camel/itest/TypeConverter", CamelJacksonFallbackConverterTest.class.getClassLoader());
        bundle.add("META-INF/services/org/apache/camel/TypeConverter", converterDeclaration);
        bundle.add(MyConverter.class, InnerClassStrategy.NONE);
        // set bundle headers
        bundle.set("Manifest-Version", "2")
                .set("Bundle-ManifestVersion", "2")
                .set("Bundle-SymbolicName", name)
                .set("Bundle-Version", "1.0.0")
                .set(Constants.DYNAMICIMPORT_PACKAGE, "*");
        // start bundle
        Bundle answer = this.bundleContext.installBundle(name, bundle.build());
        answer.start();

        // lookup Camel from OSGi
        CamelContext camel = getOsgiService(bundleContext, CamelContext.class);

        final Pojo pojo = new Pojo();
        String pojoName = "Constantine";
        pojo.setName(pojoName);

        final DefaultExchange exchange = new DefaultExchange(camel);
        final String string = camel.getTypeConverter().mandatoryConvertTo(String.class, exchange, pojo);
        LOG.info("POJO -> String: {}", string);
        final Pojo copy = camel.getTypeConverter().mandatoryConvertTo(Pojo.class, exchange, string);
        LOG.info("String -> POJO: {}", copy);
        Assert.assertEquals(pojoName, copy.getName());
    }

    @Configuration
    public Option[] configure() {
        return CamelKarafTestSupport.configure("camel-test-karaf");
    }

    @ProbeBuilder
    public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
        // Export Pojo class for TypeConverter bundle
        probe.setHeader(Constants.EXPORT_PACKAGE, "org.apache.camel.itest");
        return probe;
    }





}