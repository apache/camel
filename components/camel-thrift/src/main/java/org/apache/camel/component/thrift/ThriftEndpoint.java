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
package org.apache.camel.component.thrift;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SynchronousDelegateProducer;

/**
 * Call and expose remote procedures (RPC) with Apache Thrift data format and serialization mechanism.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "thrift", title = "Thrift", syntax = "thrift:host:port/service",
             category = { Category.RPC, Category.TRANSFORMATION }, headersClass = ThriftConstants.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.LIST, Carrier.NULL },
                                   byOption = @BodyType.When(option = "synchronous", value = "true", carriers = Carrier.LIST),
                                   description = "A List of the arguments of the called method, in the order of the IDL, with"
                                                 + " the method name in the CamelThriftMethodName header. A method without"
                                                 + " arguments gives no body, or an empty List with synchronous=true"),
           consumerReply = @BodyType(value = Carrier.ANY,
                                     description = "The result of the method: the body is converted to its return type, so"
                                                   + " text works for an i32, and a struct result needs the Thrift struct."
                                                   + " Ignored for a void method. A body that does not convert gives the client an"
                                                   + " error, or a null result from the asynchronous server"),
           producerIn = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.TEXT, Carrier.NULL },
                                  description = "A List of the arguments of the method, in the order of the IDL; a body that"
                                                + " is not a List is the one argument, no body calls a method without"
                                                + " arguments. A binary argument is a ByteBuffer, not a byte array; a method"
                                                + " whose one argument is a list needs it wrapped in a List"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   description = "The result of the method, of its return type in the IDL: a number, a"
                                                 + " boolean, a String, a ByteBuffer, a Thrift struct, or a List, Set or Map;"
                                                 + " no body for a void or oneway method"))
public class ThriftEndpoint extends DefaultEndpoint implements EndpointServiceLocation {
    @UriParam
    private ThriftConfiguration configuration;
    @UriParam(defaultValue = "false", label = "advanced",
              description = "Sets whether synchronous processing should be strictly used")
    private boolean synchronous;

    private String serviceName;
    private String servicePackage;

    public ThriftEndpoint(String uri, ThriftComponent component, ThriftConfiguration config) {
        super(uri, component);
        this.configuration = config;

        // Extract service and package names from the full service name
        serviceName = ThriftUtils.extractServiceName(configuration.getService());
        servicePackage = ThriftUtils.extractServicePackage(configuration.getService());
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return "thrift";
    }

    public ThriftConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        ThriftProducer producer = new ThriftProducer(this, configuration);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(producer);
        } else {
            return producer;
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ThriftConsumer consumer = new ThriftConsumer(this, processor, configuration);
        configureConsumer(consumer);
        return consumer;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServicePackage() {
        return servicePackage;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }
}
