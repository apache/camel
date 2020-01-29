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
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.weka.WekaConfiguration.Command;
import org.apache.camel.support.DefaultProducer;

import io.nessus.weka.AssertState;
import io.nessus.weka.Dataset;
import weka.core.Instances;

public class WekaProducer extends DefaultProducer {

    public WekaProducer(WekaEndpoint endpoint) {
        super(endpoint);
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

            Message msg = exchange.getMessage();
            msg.setBody(handleReadCmd(exchange));

        } else if (Command.write == cmd) {

            Message msg = exchange.getMessage();
            msg.setBody(handleWriteCmd(exchange));

        } else if (Command.filter == cmd) {

            Message msg = exchange.getMessage();
            msg.setBody(handleFilterCmd(exchange));
            
        }
    }

    private Dataset handleReadCmd(Exchange exchange) throws Exception {
        
        String fpath = getConfiguration().getPath();
        
        if (fpath != null) {
            Dataset dataset = Dataset.create(fpath);
            return dataset;
        }
        
        Dataset dataset = assertDatasetBody(exchange);
        return dataset;
    }

    private Object handleWriteCmd(Exchange exchange) throws Exception {
        
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

    private Dataset handleFilterCmd(Exchange exchange) throws Exception {
        
        String applyValue = getConfiguration().getApply();

        Dataset dataset = assertDatasetBody(exchange);
        dataset = dataset.apply(applyValue);
        
        return dataset;
    }

    private Dataset assertDatasetBody(Exchange exchange) throws Exception {
        
        Message msg = exchange.getMessage();
        Object body = msg.getBody();
        
        Dataset dataset = msg.getBody(Dataset.class);
        
        if (dataset == null) {
            
            if (body instanceof Instances) {

                dataset = Dataset.create((Instances) body);
                
            } else if (body instanceof GenericFile) {
                
                GenericFile<?> file = (GenericFile<?>) body;
                AssertState.isFalse(file.isDirectory(), "Directory not supported: " + file);
                String absolutePath = file.getAbsoluteFilePath();
                dataset = Dataset.create(absolutePath);
                
            } else if (body instanceof URL) {
                
                URL url = (URL) body;
                dataset = Dataset.create(url);
                
            } else if (body instanceof InputStream) {
                
                InputStream input = (InputStream) body;
                dataset = Dataset.create(input);
            }
        }
        
        AssertState.notNull(dataset, "Cannot obtain datasset from body: " + body);
        return dataset;
    }
}
