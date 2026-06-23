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
package org.apache.camel.dataformat.barcode;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;

public class BarcodeDataFormatSpringTest extends BarcodeDataFormatCamelTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        testConfiguration().withUseRouteBuilder(false);
        Map<String, String> props = new HashMap<>();
        props.put(CamelSpringTestSupport.TEST_CLASS_NAME_PROPERTY, getClass().getName());
        props.put(CamelSpringTestSupport.TEST_CLASS_SIMPLE_NAME_PROPERTY, getClass().getSimpleName());
        props.put(CamelSpringTestSupport.TEST_DIRECTORY_PROPERTY, testDirectory.toString());
        AbstractXmlApplicationContext applicationContext
                = CamelSpringTestSupport.newAppContext("barcodeDataformatSpring.xml", getClass(), props);
        return applicationContext.getBean(SpringCamelContext.class);
    }

}
