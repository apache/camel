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

import org.apache.camel.CamelContext;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.karaf.AbstractFeatureTest;
import org.apache.camel.test.karaf.CamelKarafTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class CamelJacksonFallbackConverterTest extends AbstractFeatureTest {

    @Test
    public void test() throws Exception {
        // install the camel blueprint xml file we use in this test
        URL url = ObjectHelper.loadResourceAsURL("org/apache/camel/itest/CamelJacksonFallbackConverterTest.xml", CamelJacksonFallbackConverterTest.class.getClassLoader());
        installBlueprintAsBundle("CamelJacksonFallbackConverterTest", url, true);

        // lookup Camel from OSGi
        CamelContext camel = getOsgiService(bundleContext, CamelContext.class);

        // enable Jackson json type converter
        camel.getProperties().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        // allow Jackson json to convert to pojo types also (by default jackson only converts to String and other simple types)
        camel.getProperties().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");

        final Pojo pojo = new Pojo(1337, "Constantine");

        final DefaultExchange exchange = new DefaultExchange(camel);
        final String string = camel.getTypeConverter().mandatoryConvertTo(String.class, exchange, pojo);
        LOG.info("POJO -> String: {}", string);
        final Pojo copy = camel.getTypeConverter().mandatoryConvertTo(Pojo.class, exchange, string);
        LOG.info("String -> POJO: {}", copy);
        Assert.assertEquals(pojo, copy);
    }

    @Configuration
    public Option[] configure() {
        return CamelKarafTestSupport.configure("camel-test-karaf", "camel-jackson");
    }

}