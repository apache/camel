## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
package ${package};

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Represents a ${name} endpoint.
 */
@UriEndpoint(scheme = "${scheme}", consumerClass = ${name}Consumer.class, label = "${name}")
public class ${name}Endpoint extends DefaultEndpoint {
    @UriParam(defaultValue = "10")
    private int option1 = 10;

    public ${name}Endpoint() {
    }

    public ${name}Endpoint(String uri, ${name}Component component) {
        super(uri, component);
    }

    public ${name}Endpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        return new ${name}Producer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new ${name}Consumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }
}
