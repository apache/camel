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

import java.io.ByteArrayInputStream;
import java.nio.file.Paths;

import io.nessus.weka.AssertState;
import io.nessus.weka.Dataset;
import io.nessus.weka.ModelLoader;
import io.nessus.weka.ModelPersister;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.weka.WekaConfiguration.Command;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

@SuppressWarnings("checkstyle:rightcurly")
public class WekaProducer extends DefaultProducer {

    static final Logger LOG = LoggerFactory.getLogger(WekaProducer.class);

    public WekaProducer(WekaEndpoint endpoint) {
        super(endpoint);

        // All commands are supported on the producer
        Command cmd = getConfiguration().getCommand();
        AssertState.notNull(cmd, "Null command");
    }

    @Override
    public WekaEndpoint getEndpoint() {
        return (WekaEndpoint)super.getEndpoint();
    }

    public WekaConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        WekaEndpoint endpoint = getEndpoint();
        Command cmd = getConfiguration().getCommand();

        if (Command.version == cmd) {

            Message msg = exchange.getMessage();
            msg.setBody(endpoint.wekaVersion());

        } else if (Command.read == cmd) {

            Dataset dataset = handleReadCmd(exchange);
            exchange.getMessage().setBody(dataset);

        } else if (Command.write == cmd) {

            Object result = handleWriteCmd(exchange);
            exchange.getMessage().setBody(result);

        } else if (Command.filter == cmd) {

            Dataset dataset = handleFilterCmd(exchange);
            exchange.getMessage().setBody(dataset);
            
        } else if (Command.model == cmd) {

            Dataset dataset = handleModelCmd(exchange);
            exchange.getMessage().setBody(dataset);
            
        } else if (Command.push == cmd) {

            Dataset dataset = handlePushCmd(exchange);
            exchange.getMessage().setBody(dataset);
            
        } else if (Command.pop == cmd) {

            Dataset dataset = handlePopCmd(exchange);
            exchange.getMessage().setBody(dataset);
            
        } else {
            
            // Not really needed here, because all commands are supported 
            throw new UnsupportedOperationException("Unsupported on Producer: " + cmd);
        }
    }

    Dataset handlePushCmd(Exchange exchange) throws Exception {
        
        String dsname = getConfiguration().getDsname();

        Dataset dataset = assertDatasetBody(exchange);
        if (dsname != null) {
            dataset.push(dsname);
        } else {
            dataset.push();
        }
        
        return dataset;
    }

    Dataset handlePopCmd(Exchange exchange) throws Exception {
        
        String dsname = getConfiguration().getDsname();

        Dataset dataset = assertDatasetBody(exchange);
        if (dsname != null) {
            dataset.pop(dsname);
        } else {
            dataset.pop();
        }
        
        return dataset;
    }

    Dataset handleReadCmd(Exchange exchange) throws Exception {
        
        String fpath = getConfiguration().getPath();
        
        if (fpath != null) {
            Dataset dataset = Dataset.create(fpath);
            return dataset;
        }
        
        Dataset dataset = assertDatasetBody(exchange);
        return dataset;
    }

    Object handleWriteCmd(Exchange exchange) throws Exception {
        
        Dataset dataset = assertDatasetBody(exchange);
        String fpath = getConfiguration().getPath();
        
        if (fpath != null) {
            
            dataset.write(Paths.get(fpath));
            return dataset;
            
        } else {
            
            // The internal implementation of DataSink does this.. 
            // Instances.toString().getBytes()
            //
            // Therefore, we avoid creating yet another copy of the
            // instance data and call Instances.toString() as well
            
            Instances instances = dataset.getInstances();
            byte[] bytes = instances.toString().getBytes();
            return new ByteArrayInputStream(bytes);
        }
    }

    Dataset handleFilterCmd(Exchange exchange) throws Exception {
        
        String applyValue = getConfiguration().getApply();

        Dataset dataset = assertDatasetBody(exchange);
        dataset = dataset.apply(applyValue);
        
        return dataset;
    }

    Dataset handleModelCmd(Exchange exchange) throws Exception {
        
        Dataset dataset = assertDatasetBody(exchange);
        
        String dsname = getConfiguration().getDsname();
        boolean crossValidate = getConfiguration().isXval();
        String buildSpec = getConfiguration().getBuild();
        String loadFrom = getConfiguration().getLoadFrom();
        String saveTo = getConfiguration().getSaveTo();
        
        // Load the Model
        
        if (loadFrom != null) {
            
            Classifier cl = dataset
                    .loadClassifier(new ModelLoader(loadFrom))
                    .getClassifier();
            
            AssertState.notNull(cl, "Cannot load the classifier from: " + loadFrom);
            LOG.debug("{}", cl);
        }
        
        // Build a classifier
        
        else if (buildSpec != null) {
            
            dataset.buildClassifier(buildSpec);
            
            // Cross Validate the Model
            
            if (crossValidate) {
                int seed = getConfiguration().getSeed();
                int folds = getConfiguration().getFolds();
                dataset.crossValidateModel(folds, seed);
            }
            
            // Validate the Model using explicit/current instances
            
            else {
                
                // Use the named data set training
                if (dsname != null) {
                    dataset.pop(dsname);
                }
                
                // Train with current instances
                dataset.evaluateModel();
            }
            
            Classifier cl = dataset.getClassifier();
            AssertState.notNull(cl, "Model command requires 'load' or 'apply'");
            LOG.debug("{}", cl);
            
            Evaluation ev = dataset.getEvaluation();
            LOG.debug("{}", ev.toSummaryString());
        }
        
        // Save the Model
        
        if (saveTo != null) {
            dataset.consumeClassifier(new ModelPersister(saveTo));
        }
        
        return dataset;
    }

    private Dataset assertDatasetBody(Exchange exchange) throws Exception {
        
        Message msg = exchange.getMessage();
        Dataset dataset = msg.getBody(Dataset.class);
        
        AssertState.notNull(dataset, "Cannot obtain dataset from body: " + msg.getBody());
        
        return dataset;
    }
}
