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
package org.apache.camel.component.bonita;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.bonita.consumer.BonitaConsumer;
import org.apache.camel.component.bonita.exception.BonitaException;
import org.apache.camel.component.bonita.producer.BonitaStartProducer;
import org.apache.camel.component.bonita.util.BonitaOperation;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a bonita endpoint.
 */
@UriEndpoint(scheme = "bonita", title = "bonita", syntax="bonita:operation", consumerClass = BonitaConsumer.class, label = "bonita")
public class BonitaEndpoint extends DefaultEndpoint {

    private static final transient Logger LOG = LoggerFactory.getLogger(BonitaEndpoint.class);
	
 
    @UriParam
    private BonitaConfiguration configuration;

    public BonitaEndpoint() {
    }
    
    public BonitaEndpoint(String uri, BonitaComponent component, BonitaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public BonitaEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        if (configuration.getOperation() == BonitaOperation.startCase) {
	        return new BonitaStartProducer(this, configuration);
        } else {
        	throw new BonitaException("Operation specified is not supported.");
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new BonitaConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }
    
    public BonitaConfiguration getConfiguration() {
    	return configuration;
    }


}
