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
package org.apache.camel.itest.karaf.converters;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.itest.karaf.BaseKarafTest;
import org.apache.camel.itest.karaf.bean.Pojo;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.junit.Assert.assertNotNull;

@RunWith(PaxExam.class)
public class CamelJacksonFallbackConverterTest extends BaseKarafTest {

    @Configuration
    public static Option[] configure() {
        return BaseKarafTest.configure("camel-jackson");
    }

    @Test
    public void test() throws Exception {
        CamelContext context = getOsgiService(bundleContext, CamelContext.class, "(camel.context.name=myCamel)", SERVICE_TIMEOUT);
        assertNotNull("Cannot find CamelContext with name myCamel", context);

        // enable Jackson json type converter
        context.getProperties().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        // allow Jackson json to convert to pojo types also (by default jackson only converts to String and other simple types)
        context.getProperties().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");

        // test type conversion
        final Pojo pojo = new Pojo(1337, "Constantine");
        final DefaultExchange exchange = new DefaultExchange(context);
        final String string = context.getTypeConverter().mandatoryConvertTo(String.class, exchange, pojo);
        final Pojo copy = context.getTypeConverter().mandatoryConvertTo(Pojo.class, exchange, string);
        Assert.assertEquals(pojo, copy);
    }


}