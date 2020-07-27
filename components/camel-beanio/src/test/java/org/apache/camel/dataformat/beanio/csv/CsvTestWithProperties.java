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
package org.apache.camel.dataformat.beanio.csv;

import java.util.Properties;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.beanio.BeanIODataFormat;

public class CsvTestWithProperties extends CsvTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // START SNIPPET: e1
                // setup beanio data format using the mapping file, loaded from the classpath
                BeanIODataFormat format = new BeanIODataFormat(
                        "org/apache/camel/dataformat/beanio/csv/mappingsWithProperties.xml",
                        "stream1");
                Properties properties = new Properties();
                properties.setProperty("field1", "firstName");
                properties.setProperty("field2", "lastName");

                format.setProperties(properties);

                // a route which uses the bean io data format to format a CSV data
                // to java objects
                from("direct:unmarshal")
                        .unmarshal(format)
                        // and then split the message body so we get a message for each row
                        .split(body())
                        .to("mock:beanio-unmarshal");

                // convert list of java objects back to flat format
                from("direct:marshal")
                        .marshal(format)
                        .to("mock:beanio-marshal");
                // END SNIPPET: e1
            }
        };
    }

}
