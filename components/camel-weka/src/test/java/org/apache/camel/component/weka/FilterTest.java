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
package org.apache.camel.component.weka;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.nessus.weka.Dataset;
import io.nessus.weka.utils.DatasetUtils;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;
import weka.core.Instances;

public class FilterTest {

    @Test
    public void readFromFileFilterAndWrite() throws Exception {

        try (CamelContext camelctx = new DefaultCamelContext()) {

            camelctx.addRoutes(new RouteBuilder() {

                @Override
                public void configure() throws Exception {

                        // Use the file component to read the CSV file
                        from("file:src/test/resources/data?fileName=sfny.csv&noop=true")

                        // Convert the 'in_sf' attribute to nominal
                        .to("weka:filter?apply=NumericToNominal -R first")

                        // Move the 'in_sf' attribute to the end
                        .to("weka:filter?apply=Reorder -R 2-last,1")

                        // Rename the relation
                        .to("weka:filter?apply=RenameRelation -modify sfny")

                        // Use the file component to write the Arff file
                        .to("file:target/data?fileName=sfny.arff")

                        .to("direct:end");
                }
            });
            camelctx.start();

            ConsumerTemplate consumer = camelctx.createConsumerTemplate();
            Dataset dataset = consumer.receiveBody("direct:end", Dataset.class);
            Assert.assertEquals("sfny", dataset.getInstances().relationName());
            
            Instances instances = DatasetUtils.read("target/data/sfny.arff");
            Assert.assertEquals("sfny", instances.relationName());
        }
    }

    @Test
    public void readWithWekaFilterAndWrite() throws Exception {

        try (CamelContext camelctx = new DefaultCamelContext()) {

            camelctx.addRoutes(new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    
                        // Use weka to read the CSV file
                        from("direct:start")
                    
                        // Convert the 'in_sf' attribute to nominal
                        .to("weka:filter?apply=NumericToNominal -R first")
                        
                        // Move the 'in_sf' attribute to the end
                        .to("weka:filter?apply=Reorder -R 2-last,1")
                        
                        // Rename the relation
                        .to("weka:filter?apply=RenameRelation -modify sfny")
                        
                        // Use weka to write the Arff file
                        .to("weka:write?path=target/data/sfny.arff");
                }
            });
            camelctx.start();

            Path inpath = Paths.get("src/test/resources/data/sfny.csv");

            ProducerTemplate producer = camelctx.createProducerTemplate();
            Dataset dataset = producer.requestBody("direct:start", inpath, Dataset.class);
            Assert.assertEquals("sfny", dataset.getInstances().relationName());
            
            Instances instances = DatasetUtils.read("target/data/sfny.arff");
            Assert.assertEquals("sfny", instances.relationName());
        }
    }
}
