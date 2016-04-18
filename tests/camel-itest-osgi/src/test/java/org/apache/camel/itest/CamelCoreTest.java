/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.itest;

import java.net.URL;

import org.apache.camel.CamelContext;
import org.apache.camel.test.karaf.CamelKarafTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class CamelCoreTest extends CamelKarafTestSupport {

    @Test
    public void testCamelCore() throws Exception {
        URL url = ObjectHelper.loadResourceAsURL("org/apache/camel/itest/CamelCoreTest.xml", CamelCoreTest.class.getClassLoader());
        System.out.println(">>>> " + url);
        installBlueprintAsBundle("CamelCoreTest", url);

        // wait for Camel to be ready
//        CamelContext camel = getOsgiService(CamelContext.class);

//        System.out.println(">>> " + camel);
    }

    @Configuration
    public Option[] configure() {
        return CamelKarafTestSupport.configure();
    }

}
