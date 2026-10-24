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
package org.apache.camel.component.avro;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import org.apache.avro.Protocol;
import org.apache.avro.reflect.ReflectData;
import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.avro.support.AvroClassSecuritySupport;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Produce or consume Apache Avro RPC services.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "avro", title = "Avro RPC", syntax = "avro:transport:host:port/messageName",
             category = { Category.RPC }, headersClass = AvroConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.LIST, javaType = "java.lang.Object[]",
                                   byOption = @BodyType.When(option = "singleParameter", value = "true",
                                                             carriers = Carrier.UNKNOWN),
                                   description = "An Object array of the parameters of the called message, in the order of"
                                                 + " the protocol, with the message name in the CamelAvroMessageName header."
                                                 + " With singleParameter the one parameter itself, such as a record"),
           consumerReply = @BodyType(value = Carrier.ANY,
                                     description = "The response of the message, of its type in the protocol, such as the"
                                                   + " record class; it is not converted, so text or a Map fails for a record."
                                                   + " Ignored for a message with a null response"),
           producerIn = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.TEXT, Carrier.VALUE },
                                  description = "An Object array of the parameters of the message, in the order of the"
                                                + " protocol, or the one parameter itself, such as a record. A java.util.List"
                                                + " is not unpacked and a Map is not a record: build the parameter types of"
                                                + " the protocol. The message name comes from the CamelAvroMessageName header,"
                                                + " or else from the endpoint"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   description = "The response of the message, of its type in the protocol, such as the"
                                                 + " record class; no body for a message with a null response"))
public abstract class AvroEndpoint extends DefaultEndpoint implements AsyncEndpoint {

    @UriParam
    private AvroConfiguration configuration;

    protected AvroEndpoint(String endpointUri, Component component, AvroConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public boolean isSingletonProducer() {
        return false;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AvroConsumer consumer = new AvroConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public AvroConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        validateConfiguration(configuration);
    }

    /**
     * Validates configuration
     */
    private void validateConfiguration(AvroConfiguration config) throws Exception {
        if (config.getProtocol() == null && config.getProtocolClassName() != null) {
            Class<?> protocolClass = getCamelContext().getClassResolver().resolveClass(config.getProtocolClassName());
            if (protocolClass != null) {
                try {
                    Field f = protocolClass.getField("PROTOCOL");
                    if (f != null) {
                        Protocol protocol = (Protocol) f.get(null);
                        config.setProtocol(protocol);
                    }
                } catch (NoSuchFieldException e) {
                    ReflectData reflectData = ReflectData.get();
                    config.setProtocol(reflectData.getProtocol(protocolClass));
                    config.setReflectionProtocol(true);
                }
            }
        }

        if (config.getProtocol() == null) {
            throw new IllegalArgumentException("Avro configuration does not contain protocol");
        }

        configureClassSecurity(config);

        if (config.getMessageName() != null && !config.getProtocol().getMessages().containsKey(config.getMessageName())) {
            throw new IllegalArgumentException("Message " + config.getMessageName() + " is not defined in protocol");
        }

        if (config.isSingleParameter()) {
            Map<String, Protocol.Message> messageMap = config.getProtocol().getMessages();
            Iterable<Protocol.Message> messagesToCheck = config.getMessageName() == null
                    ? messageMap.values()
                    : Collections.singleton(messageMap.get(config.getMessageName()));
            for (Protocol.Message message : messagesToCheck) {
                if (message.getRequest().getFields().size() != 1) {
                    throw new IllegalArgumentException(
                            "Single parameter option can't be used with message "
                                                       + message.getName() + " because it has "
                                                       + message.getRequest().getFields().size()
                                                       + " parameters defined");
                }
            }
        }
    }

    private void configureClassSecurity(AvroConfiguration config) {
        AvroClassSecuritySupport.trustPackages(config.getSerializablePackages());
        AvroClassSecuritySupport.trustClassName(config.getProtocolClassName());
        if (config.getProtocol() != null) {
            AvroClassSecuritySupport.trustProtocol(config.getProtocol());
        }
    }
}
