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
package org.apache.camel.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The message body a component, data format or language works with: the body it needs, and the body it leaves after it.
 * The build carries it into the catalog, so tooling can say what the body is after a step without running the route:
 * the validators, the catalog tools and the documentation.
 * <p/>
 * Put it on the endpoint class (the class with {@link UriEndpoint}), the data format class or the language class. A
 * subclass does not have the body types of its superclass, as it may change the body: it declares its own.
 * <p/>
 * Each side that is given says what the body is with the default options. A side that is not given is not known, and
 * tooling must not guess it. A side whose body depends on what the other system sends, or on a class the user
 * configures, says {@link BodyType.Carrier#UNKNOWN}.
 * <p/>
 * For example the file component: the consumer gives the file it read, the producer writes text, bytes, a stream or a
 * file, and leaves the body as it was:
 *
 * <pre>
 * &#64;BodyTypes(consumerOut = &#64;BodyType(value = Carrier.FILE, description = "The file as read"),
 *            producerIn = &#64;BodyType({ Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
 *            producerOut = &#64;BodyType(Carrier.UNCHANGED))
 * </pre>
 *
 * @since 4.24
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.TYPE })
public @interface BodyTypes {

    /**
     * The body of the message the consumer creates.
     */
    BodyType consumerOut() default @BodyType;

    /**
     * The bodies a consumer that answers the caller can reply with: the body the route ends with, such as the response
     * of an HTTP server.
     */
    BodyType consumerReply() default @BodyType;

    /**
     * The bodies the producer can send or write.
     */
    BodyType producerIn() default @BodyType;

    /**
     * The body after the producer: {@link BodyType.Carrier#UNCHANGED} when it leaves the body as it was, otherwise what
     * it sets, such as the response of a call.
     */
    BodyType producerOut() default @BodyType;

    /**
     * The bodies a data format can marshal.
     */
    BodyType marshalIn() default @BodyType;

    /**
     * The body after marshal.
     */
    BodyType marshalOut() default @BodyType;

    /**
     * The bodies a data format can unmarshal.
     */
    BodyType unmarshalIn() default @BodyType;

    /**
     * The body after unmarshal.
     */
    BodyType unmarshalOut() default @BodyType;

    /**
     * The bodies the expressions of a language can read.
     */
    BodyType expressionIn() default @BodyType;

    /**
     * The result of an expression of a language, which becomes the body in setBody and transform.
     */
    BodyType expressionOut() default @BodyType;

}
