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
import io.nessus.weka.testing.AbstractWekaTest;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;

public class DecisionTreeTest extends AbstractWekaTest {
    
    @Test
    public void testJ48WithCrossValidation() throws Exception {
        
        try (CamelContext camelctx = new DefaultCamelContext()) {
            
            camelctx.addRoutes(new RouteBuilder() {
                
                @Override
                public void configure() throws Exception {
                    
                        // Use the weka to read the model training data
                        from("direct:start")
                        
                        // Build a J48 classifier using cross-validation with 10 folds
                        .to("weka:model?build=J48&xval=true&folds=10&seed=1")
                        
                        // Persist the J48 model
                        .to("weka:model?saveTo=src/test/resources/data/sfny-j48.model");
                }
            });
            camelctx.start();
            
            Path inpath = Paths.get("src/test/resources/data/sfny-train.arff");
            
            ProducerTemplate producer = camelctx.createProducerTemplate();
            Dataset dataset = producer.requestBody("direct:start", inpath, Dataset.class);
            
            Classifier classifier = dataset.getClassifier();
            Assert.assertNotNull(classifier);
            logInfo("{}", classifier);
            
            Evaluation evaluation = dataset.getEvaluation();
            Assert.assertNotNull(evaluation);
            logInfo("{}", evaluation);
        }
    }
    
    @Test
    public void testJ48WithTrainingData() throws Exception {
        
        try (CamelContext camelctx = new DefaultCamelContext()) {
            
            camelctx.addRoutes(new RouteBuilder() {
                
                @Override
                public void configure() throws Exception {
                    
                        // Use the weka to read the model training data
                        from("direct:start")
                        
                        // Push the current instances to the stack
                        .to("weka:push?dsname=sfny-train")
                        
                        // Build a J48 classifier with a set of named instances
                        .to("weka:model?build=J48&dsname=sfny-train");
                }
            });
            camelctx.start();
            
            Path inpath = Paths.get("src/test/resources/data/sfny-train.arff");
            
            ProducerTemplate producer = camelctx.createProducerTemplate();
            Dataset dataset = producer.requestBody("direct:start", inpath, Dataset.class);
            
            Classifier classifier = dataset.getClassifier();
            Assert.assertNotNull(classifier);
            logInfo("{}", classifier);
            
            Evaluation evaluation = dataset.getEvaluation();
            Assert.assertNotNull(evaluation);
            logInfo("{}", evaluation);
        }
    }
    
    @Test
    public void testJ48WithCurrentInstances() throws Exception {
        
        try (CamelContext camelctx = new DefaultCamelContext()) {
            
            camelctx.addRoutes(new RouteBuilder() {
                
                @Override
                public void configure() throws Exception {
                    
                        // Use the weka to read the model training data
                        from("direct:start")
                        
                        // Build a J48 classifier using the current dataset
                        .to("weka:model?build=J48");
                }
            });
            camelctx.start();
            
            Path inpath = Paths.get("src/test/resources/data/sfny-train.arff");
            
            ProducerTemplate producer = camelctx.createProducerTemplate();
            Dataset dataset = producer.requestBody("direct:start", inpath, Dataset.class);
            
            Classifier classifier = dataset.getClassifier();
            Assert.assertNotNull(classifier);
            logInfo("{}", classifier);
            
            Evaluation evaluation = dataset.getEvaluation();
            Assert.assertNotNull(evaluation);
            logInfo("{}", evaluation);
        }
    }
}
