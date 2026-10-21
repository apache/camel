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
package org.apache.camel.maven.packaging.endpoint;

import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;

/**
 * Body types that do not fit an endpoint, one per nested class.
 */
public final class SomeEndpointWithBadBodyTypes {

    private SomeEndpointWithBadBodyTypes() {
    }

    @UriEndpoint(scheme = "some", syntax = "some", title = "some")
    @BodyTypes(marshalOut = @BodyType(Carrier.STREAM))
    public static class DataFormatSide {
    }

    @UriEndpoint(scheme = "some", syntax = "some", title = "some", consumerOnly = true)
    @BodyTypes(producerOut = @BodyType(Carrier.UNCHANGED))
    public static class ProducerSideOfConsumer {
    }

    @UriEndpoint(scheme = "some", syntax = "some", title = "some")
    @BodyTypes(producerIn = @BodyType(Carrier.UNCHANGED))
    public static class UnchangedInput {
    }

    @UriEndpoint(scheme = "some", syntax = "some", title = "some")
    @BodyTypes(producerOut = @BodyType(Carrier.ANY))
    public static class AnyOutput {
    }

    @UriEndpoint(scheme = "some", syntax = "some", title = "some")
    @BodyTypes(producerOut = @BodyType({ Carrier.UNKNOWN, Carrier.TEXT }))
    public static class UnknownWithOthers {
    }

    @UriEndpoint(scheme = "some", syntax = "some", title = "some")
    @BodyTypes(producerOut = @BodyType(javaType = "java.lang.String"))
    public static class NoCarriers {
    }

    @UriEndpoint(scheme = "some", syntax = "some", title = "some")
    @BodyTypes(producerOut = @BodyType(value = Carrier.TEXT, dependsOn = "nope"))
    public static class UnknownOption {
    }

    @UriEndpoint(scheme = "some", syntax = "some", title = "some")
    @BodyTypes(producerOut = @BodyType(value = Carrier.TEXT,
                                       byOption = @BodyType.When(option = "output", value = "bytes", carriers = {})))
    public static class OptionValueWithoutCarriers {
    }

    @UriEndpoint(scheme = "some", syntax = "some", title = "some")
    @BodyTypes(producerOut = @BodyType(value = Carrier.TEXT,
                                       byOption = @BodyType.When(option = "output", value = "DOM", carrier = Carrier.POJO)))
    public static class ValueTheOptionDoesNotHave {
    }
}
