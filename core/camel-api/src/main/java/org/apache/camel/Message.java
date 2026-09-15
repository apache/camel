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
package org.apache.camel;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.trait.message.MessageTrait;
import org.jspecify.annotations.Nullable;

/**
 * Implements the <a href="https://camel.apache.org/message.html">Message</a> pattern and represents an inbound or
 * outbound message as part of an {@link Exchange}.
 * <p/>
 * Headers are represented in Camel using a {@link org.apache.camel.util.CaseInsensitiveMap CaseInsensitiveMap}. The
 * implementation of the map can be configured by the {@link HeadersMapFactory} which can be set on the
 * {@link CamelContext}. The default implementation uses the {@link org.apache.camel.util.CaseInsensitiveMap
 * CaseInsensitiveMap}.
 *
 * @see Exchange
 * @see ExchangePattern
 */
@Metadata(label = "api",
          description = "The body and the headers of the exchange, from exchange.getMessage(). getBody(type) converts "
                        + "the body with the type converters (a Map body has no properties: read map.get(\"type\"), not "
                        + "${body.type}); getHeader(name) is case-insensitive and null when the header is absent; setBody "
                        + "replaces the body and keeps the headers.")
public interface Message {

    /**
     * Returns a new instance of this type.
     */
    Message newInstance();

    /**
     * Clears the message from user data, so the message can be reused.
     * <p/>
     * <b>Important:</b> This API is NOT intended for Camel end users, but used internally by Camel itself.
     */
    void reset();

    /**
     * Returns the id of the message.
     * <p/>
     * By default, the message uses the same id as {@link Exchange#getExchangeId()} as messages are associated with the
     * exchange and using different IDs does not offer much value. Another reason is to optimize for performance to
     * avoid generating new IDs.
     * <p/>
     * A few Camel components do provide their own message IDs such as the JMS components.
     *
     * @return the message id
     */
    String getMessageId();

    /**
     * Returns the timestamp that this messages originates from.
     * <p/>
     * Some systems like JMS, Kafka, AWS have a timestamp on the event/message, that Camel received. This method returns
     * the timestamp, if a timestamp exists.
     * <p/>
     * The message timestamp and exchange created are not the same. An exchange always have a created timestamp which is
     * the local timestamp when Camel created the exchange. The message timestamp is only available in some Camel
     * components when the consumer is able to extract the timestamp from the source event.
     *
     * @return the timestamp, or <tt>0</tt> if the message has no source timestamp.
     * @see    Exchange#getClock()
     * @since  3.11
     */
    long getMessageTimestamp();

    /**
     * Sets the id of the message
     *
     * @param messageId id of the message
     */
    void setMessageId(String messageId);

    /**
     * Whether the message has any message ID assigned.
     *
     * @since 3.11
     */
    boolean hasMessageId();

    /**
     * Returns the exchange this message is related to
     *
     * @return the exchange
     */
    @Nullable
    @Metadata(label = "api", description = "The exchange this message belongs to (properties, variables, context).")
    Exchange getExchange();

    /**
     * Accesses a specific header
     *
     * @param  name name of header
     * @return      the value of the given header or <tt>null</tt> if there is no header for the given name
     */
    @Nullable
    Object getHeader(String name);

    /**
     * Accesses a specific header
     *
     * @param  name         name of header
     * @param  defaultValue the default value to return if header was absent
     * @return              the value of the given header or <tt>defaultValue</tt> if there is no header for the given
     *                      name
     */
    Object getHeader(String name, Object defaultValue);

    /**
     * Accesses a specific header
     *
     * @param  name                 name of header
     * @param  defaultValueSupplier the default value supplier used to generate the value to return if header was absent
     * @return                      the value of the given header or the value generated by the
     *                              <tt>defaultValueSupplier</tt> if there is no header for the given name
     */
    Object getHeader(String name, Supplier<Object> defaultValueSupplier);

    /**
     * Returns a header associated with this message by name and specifying the type required
     *
     * @param  name                    the name of the header
     * @param  type                    the type of the header
     * @return                         the value of the given header or <tt>null</tt> if there is no header for the
     *                                 given name
     * @throws TypeConversionException is thrown if error during type conversion
     */
    @Metadata(label = "api",
              important = true,
              description = "A header by name (case-insensitive), converted to the type; null when absent, or the default "
                            + "value when one is given.",
              examples = {
                      "message.getHeader(\"CamelFileName\", String.class)",
                      "message.getHeader(\"count\", 0, Integer.class)" })
    <T> @Nullable T getHeader(String name, Class<T> type);

    /**
     * Returns a header associated with this message by name and specifying the type required
     *
     * @param  name         the name of the header
     * @param  defaultValue the default value to return if header was absent
     * @param  type         the type of the header
     * @return              the value of the given header or <tt>defaultValue</tt> if there is no header for the given
     *                      name or <tt>null</tt> if it cannot be converted to the given type
     */
    <T> @Nullable T getHeader(String name, Object defaultValue, Class<T> type);

    /**
     * Returns a header associated with this message by name and specifying the type required
     *
     * @param  name                 the name of the header
     * @param  defaultValueSupplier the default value supplier used to generate the value to return if header was absent
     * @param  type                 the type of the header
     * @return                      the value of the given header or the value generated by the
     *                              <tt>defaultValueSupplier</tt> if there is no header for the given name or
     *                              <tt>null</tt> if it cannot be converted to the given type
     */
    <T> @Nullable T getHeader(String name, Supplier<Object> defaultValueSupplier, Class<T> type);

    /**
     * Sets a header on the message
     *
     * @param name  of the header
     * @param value to associate with the name
     */
    @Metadata(label = "api",
              important = true,
              description = "Sets a header; headers travel with the message to the endpoints (as HTTP headers, JMS "
                            + "properties, Kafka headers).",
              examples = { "message.setHeader(\"CamelHttpMethod\", \"POST\")" })
    void setHeader(String name, @Nullable Object value);

    /**
     * Removes the named header from this message
     *
     * @param  name name of the header
     * @return      the old value of the header
     */
    @Nullable
    @Metadata(label = "api",
              description = "Removes a header; removeHeaders(pattern) removes by wildcard, e.g. \"Camel*\" strips the Camel "
                            + "ones.")
    Object removeHeader(String name);

    /**
     * Removes the headers from this message
     *
     * @param  pattern pattern of names
     * @return         boolean whether any headers matched
     */
    boolean removeHeaders(String pattern);

    /**
     * Removes the headers from this message that match the given <tt>pattern</tt>, except for the ones matching one or
     * more <tt>excludePatterns</tt>
     *
     * @param  pattern         pattern of names that should be removed
     * @param  excludePatterns one or more pattern of header names that should be excluded (= preserved)
     * @return                 boolean whether any headers matched
     */
    boolean removeHeaders(String pattern, String... excludePatterns);

    /**
     * Returns all the headers associated with the message.
     * <p/>
     * Headers are represented in Camel using a {@link org.apache.camel.util.CaseInsensitiveMap CaseInsensitiveMap}. The
     * implementation of the map can be configured by the {@link HeadersMapFactory} which can be set on the
     * {@link CamelContext}. The default implementation uses the {@link org.apache.camel.util.CaseInsensitiveMap
     * CaseInsensitiveMap}.
     * <p/>
     * <b>Important:</b> If you want to walk the returned {@link Map} and fetch all the keys and values, you should use
     * the {@link java.util.Map#entrySet()} method, which ensure you get the keys in the original case.
     *
     * @return all the headers in a Map
     */
    @Metadata(label = "api",
              description = "All headers as a case-insensitive Map<String, Object>; changes to the map change the message.")
    Map<String, Object> getHeaders();

    /**
     * Set all the headers associated with this message
     * <p/>
     * <b>Important:</b> If you want to copy headers from another {@link Message} to this {@link Message}, then use
     * <tt>getHeaders().putAll(other)</tt> to copy the headers, where <tt>other</tt> is the other headers.
     *
     * @param headers headers to set
     */
    void setHeaders(Map<String, Object> headers);

    /**
     * Returns whether any headers have been set.
     *
     * @return <tt>true</tt> if any headers has been set
     */
    boolean hasHeaders();

    /**
     * Returns the body of the message as a POJO
     * <p/>
     * The body can be <tt>null</tt> if body has not been set.
     * <p/>
     * Notice if the message body is stream based then calling this method multiple times may lead to the stream not
     * being able to be re-read again. You can enable stream caching and call the {@link StreamCache#reset()} method to
     * reset the stream to be able to re-read again (if possible). See more details about
     * <a href="https://camel.apache.org/stream-caching.html">stream caching</a>.
     *
     * @return the body, can be <tt>null</tt>
     */
    @Nullable
    Object getBody();

    /**
     * Returns the body of the message as a POJO
     * <p/>
     * Notice if the message body is stream based then calling this method multiple times may lead to the stream not
     * being able to be re-read again. See more details about
     * <a href="https://camel.apache.org/stream-caching.html">stream caching</a>.
     *
     * @return                         the body, is never <tt>null</tt>
     * @throws InvalidPayloadException Is thrown if the body being <tt>null</tt> or wrong class type
     */
    Object getMandatoryBody() throws InvalidPayloadException;

    /**
     * Returns the body as the specified type
     * <p/>
     * Notice if the message body is stream based then calling this method multiple times may lead to the stream not
     * being able to be re-read again. You can enable stream caching and call the {@link StreamCache#reset()} method to
     * reset the stream to be able to re-read again (if possible). See more details about
     * <a href="https://camel.apache.org/stream-caching.html">stream caching</a>.
     * <p/>
     * The helper method {@link org.apache.camel.support.ExchangeHelper#getBodyAndResetStreamCache(Exchange, Class)} can
     * be used instead that gets the body, convert to the given type, and ensures to reset the body if its stream based.
     *
     * @param  type                    the type that the body
     * @return                         the body of the message as the specified type, or <tt>null</tt> if body does not
     *                                 exist
     * @throws TypeConversionException is thrown if error during type conversion
     * @see                            org.apache.camel.support.ExchangeHelper#getBodyAndResetStreamCache(Exchange,
     *                                 Class)
     */
    @Metadata(label = "api",
              important = true,
              description = "The body, converted to the type when one is given (String, byte[], InputStream, a POJO through "
                            + "the registered converters); null when there is no body or no conversion.",
              examples = { "message.getBody(String.class)", "message.getBody()" })
    <T> @Nullable T getBody(Class<T> type);

    /**
     * Returns the mandatory body as the specified type
     * <p/>
     * Notice if the message body is stream based then calling this method multiple times may lead to the stream not
     * being able to be re-read again. You can enable stream caching and call the {@link StreamCache#reset()} method to
     * reset the stream to be able to re-read again (if possible). See more details about
     * <a href="https://camel.apache.org/stream-caching.html">stream caching</a>.
     * <p/>
     * The helper method {@link org.apache.camel.support.ExchangeHelper#getBodyAndResetStreamCache(Exchange, Class)} can
     * be used instead that gets the body, convert to the given type, and ensures to reset the body if its stream based.
     *
     * @param  type                    the type that the body
     * @return                         the body of the message as the specified type, is never <tt>null</tt>.
     * @throws InvalidPayloadException Is thrown if the body being <tt>null</tt> or wrong class type
     * @see                            org.apache.camel.support.ExchangeHelper#getBodyAndResetStreamCache(Exchange,
     *                                 Class)
     */
    @Metadata(label = "api",
              description = "Like getBody(type) but throws InvalidPayloadException when the body is null or cannot be "
                            + "converted.")
    <T> T getMandatoryBody(Class<T> type) throws InvalidPayloadException;

    /**
     * Sets the body of the message
     *
     * @param body the body
     */
    @Metadata(label = "api",
              important = true,
              description = "Replaces the body, the headers stay; this is how a step returns its result.",
              examples = { "message.setBody(order)" })
    void setBody(@Nullable Object body);

    /**
     * Sets the body of the message as a specific type
     *
     * @param body the body
     * @param type the type of the body
     */
    <T> void setBody(@Nullable Object body, Class<T> type);

    /**
     * Creates a copy of this message so that it can be used and possibly modified further in another exchange.
     * <p/>
     * The returned {@link Message} copy will have its {@link Exchange} set to the same {@link Exchange} instance as
     * from the source.
     *
     * @return a new message instance copied from this message
     */
    Message copy();

    /**
     * Copies the contents of the other message into this message
     * <p/>
     * If you need to do a copy and then set a new body, then use {@link #copyFromWithNewBody(Message, Object)} method
     * instead.
     * <p/>
     * The returned {@link Message} copy will have its {@link Exchange} set to the same {@link Exchange} instance as
     * from the source.
     *
     * @param message the other message
     * @see           #copyFromWithNewBody(Message, Object)
     */
    void copyFrom(Message message);

    /**
     * Copies the contents (except the body) of the other message into this message and uses the provided new body
     * instead
     * <p/>
     * The returned {@link Message} copy will have its {@link Exchange} set to the same {@link Exchange} instance as
     * from the source.
     *
     * @param message the other message
     * @param newBody the new body to use
     */
    void copyFromWithNewBody(Message message, @Nullable Object newBody);

    /**
     * Checks whether the message has a given {@link MessageTrait}
     *
     * @param  trait the {@link MessageTrait} to check
     * @return       true if the message instance has the trait or false otherwise
     */
    boolean hasTrait(MessageTrait trait);

    /**
     * Gets the payload for the {@link MessageTrait}
     *
     * @param  trait the {@link MessageTrait} to obtain the payload
     * @return       The trait payload or null if not available
     */
    @Nullable
    Object getPayloadForTrait(MessageTrait trait);

    /**
     * Sets the payload for the {@link MessageTrait}
     *
     * @param trait  the {@link MessageTrait} to set the payload
     * @param object the payload
     */
    void setPayloadForTrait(MessageTrait trait, Object object);

    /**
     * Removes the trait
     */
    void removeTrait(MessageTrait trait);

}
