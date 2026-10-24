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
package org.apache.camel.component.mina;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Socket level networking using TCP or UDP with Apache Mina 2.x.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "mina", title = "Mina", syntax = "mina:protocol:host:port",
             category = { Category.NETWORKING }, headersClass = MinaConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   dependsOn = { "codec", "objectCodecPattern", "transferExchange" },
                                   byOption = {
                                           @BodyType.When(option = "protocol", value = "udp", carriers = Carrier.BYTES,
                                                          javaType = "byte[]"),
                                           @BodyType.When(option = "allowDefaultCodec", value = "false",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.apache.mina.core.buffer.IoBuffer") },
                                   description = "The data received as a String: one line with textline=true. The default"
                                                 + " object codec over TCP accepts only a String unless objectCodecPattern"
                                                 + " lists the classes to deserialize. Over UDP the datagram as a byte array"),
           consumerReply = @BodyType(value = { Carrier.NULL, Carrier.TEXT },
                                     dependsOn = { "codec", "transferExchange" },
                                     byOption = {
                                             @BodyType.When(option = "protocol", value = "udp",
                                                            carriers = {
                                                                    Carrier.NULL, Carrier.TEXT, Carrier.BYTES,
                                                                    Carrier.STREAM, Carrier.FILE }),
                                             @BodyType.When(option = "allowDefaultCodec", value = "false",
                                                            carriers = { Carrier.NULL, Carrier.POJO },
                                                            javaType = "org.apache.mina.core.buffer.IoBuffer") },
                                     description = "The body the route ends with is written back to the caller, also with"
                                                   + " sync=false. Send a String: with textline=true other bodies are"
                                                   + " written with toString(), and the object codec writes any"
                                                   + " Serializable object, which a Mina client accepts only when its"
                                                   + " objectCodecPattern lists the class. No body sends nothing and closes"
                                                   + " the connection"),
           producerIn = @BodyType(value = { Carrier.NULL, Carrier.TEXT },
                                  dependsOn = { "codec", "transferExchange" },
                                  byOption = {
                                          @BodyType.When(option = "textline", value = "true", carriers = Carrier.ANY),
                                          @BodyType.When(option = "protocol", value = "udp",
                                                         carriers = {
                                                                 Carrier.NULL, Carrier.TEXT, Carrier.BYTES,
                                                                 Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "allowDefaultCodec", value = "false",
                                                         carriers = { Carrier.NULL, Carrier.POJO },
                                                         javaType = "org.apache.mina.core.buffer.IoBuffer") },
                                  description = "The data to send: a String. The default object codec over TCP serializes"
                                                + " the body, but a Mina server accepts only a String unless its"
                                                + " objectCodecPattern lists the class, and a stream fails; textline=true"
                                                + " converts any body to a line of text. No body sends nothing"),
           producerOut = @BodyType(value = { Carrier.TEXT, Carrier.NULL }, javaType = "java.lang.String",
                                   dependsOn = { "codec", "objectCodecPattern", "transferExchange" },
                                   byOption = {
                                           @BodyType.When(option = "sync", value = "false",
                                                          carriers = { Carrier.UNCHANGED, Carrier.NULL }),
                                           @BodyType.When(option = "protocol", value = "udp",
                                                          carriers = { Carrier.BYTES, Carrier.NULL }, javaType = "byte[]"),
                                           @BodyType.When(option = "allowDefaultCodec", value = "false",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.apache.mina.core.buffer.IoBuffer") },
                                   description = "With sync=true (the default) the reply of the server as a String, or a"
                                                 + " byte array over UDP. No body is not sent and stays null. With"
                                                 + " sync=false an InOut exchange ends with an empty message"))
public class MinaEndpoint extends DefaultEndpoint implements MultipleConsumersSupport, EndpointServiceLocation {

    @UriParam
    private MinaConfiguration configuration;

    public MinaEndpoint() {
    }

    public MinaEndpoint(String endpointUri, Component component, MinaConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getProtocol() + ":" + configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return configuration.getProtocol();
    }

    @Override
    public boolean isSingletonProducer() {
        // the producer should not be singleton otherwise cannot use concurrent producers and safely
        // use request/reply with correct correlation
        return !configuration.isSync();
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        return new MinaProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        MinaConsumer answer = new MinaConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        // only datagram should allow multiple consumers
        return configuration.isDatagramProtocol();
    }

    // Properties
    // -------------------------------------------------------------------------
    public MinaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MinaConfiguration configuration) {
        this.configuration = configuration;
    }
}
