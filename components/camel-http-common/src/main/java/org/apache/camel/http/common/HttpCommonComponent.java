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
package org.apache.camel.http.common;

import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.HeaderFilterStrategyComponent;

public abstract class HttpCommonComponent extends HeaderFilterStrategyComponent {

    @Metadata(label = "advanced",
              description = "To use a custom HttpBinding to control the mapping between Camel message and HttpClient.")
    protected HttpBinding httpBinding;
    @Metadata(label = "advanced", description = "To use the shared HttpConfiguration as base configuration.")
    protected HttpConfiguration httpConfiguration;
    @Metadata(label = "consumer", defaultValue = "true",
              description = "If enabled and an Exchange failed processing on the consumer side the response's body won't contain the exception's stack trace.")
    protected boolean muteException = true;
    @Metadata(label = "advanced", security = "insecure:serialization",
              description = "Whether to allow java serialization when a request uses context-type=application/x-java-serialized-object."
                            + " This is by default turned off. "
                            + " If you enable this then be aware that Java will deserialize the incoming data from the request to Java and that can be a potential security risk.")
    protected boolean allowJavaSerializedObject;
    @Metadata(label = "advanced,security",
              description = "Sets an ObjectInputFilter pattern (jdk.serialFilter syntax) applied when deserializing"
                            + " Java objects from requests or responses with Content-Type"
                            + " application/x-java-serialized-object (only used when allowJavaSerializedObject or"
                            + " transferException is enabled). When not set, the JVM-wide jdk.serialFilter is used if"
                            + " present; otherwise a conservative default filter denying java.net.* and otherwise"
                            + " allowing java.*, javax.* and org.apache.camel.* packages is applied.")
    protected String deserializationFilter;

    protected HttpCommonComponent() {
    }

    /**
     * Gets the parameter. This method doesn't resolve reference parameters in the registry.
     *
     * @param  parameters the parameters
     * @param  key        the key
     * @param  type       the requested type to convert the value from the parameter
     * @return            the converted value parameter
     */
    public <T> T getParameter(Map<String, Object> parameters, String key, Class<T> type) {
        return getParameter(parameters, key, type, null);
    }

    /**
     * Gets the parameter. This method doesn't resolve reference parameters in the registry.
     *
     * @param  parameters   the parameters
     * @param  key          the key
     * @param  type         the requested type to convert the value from the parameter
     * @param  defaultValue use this default value if the parameter does not contain the key
     * @return              the converted value parameter
     */
    public <T> T getParameter(Map<String, Object> parameters, String key, Class<T> type, T defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            value = defaultValue;
        }
        if (value == null) {
            return null;
        }

        return CamelContextHelper.convertTo(getCamelContext(), type, value);
    }

    /**
     * Connects the URL specified on the endpoint to the specified processor.
     *
     * @param  consumer  the consumer
     * @throws Exception can be thrown
     */
    public void connect(HttpConsumer consumer) throws Exception {
    }

    /**
     * Disconnects the URL specified on the endpoint from the specified processor.
     *
     * @param  consumer  the consumer
     * @throws Exception can be thrown
     */
    public void disconnect(HttpConsumer consumer) throws Exception {
    }

    /**
     * Checks whether the consumer is possible to connect to the endoint.
     *
     * @param  consumer  the consumer
     * @throws Exception can be thrown
     */
    public boolean canConnect(HttpConsumer consumer) throws Exception {
        return true;
    }

    @Override
    protected boolean useIntrospectionOnEndpoint() {
        return false;
    }

    public HttpBinding getHttpBinding() {
        return httpBinding;
    }

    /**
     * To use a custom HttpBinding to control the mapping between Camel message and HttpClient.
     */
    public void setHttpBinding(HttpBinding httpBinding) {
        this.httpBinding = httpBinding;
    }

    public HttpConfiguration getHttpConfiguration() {
        return httpConfiguration;
    }

    /**
     * To use the shared HttpConfiguration as base configuration.
     */
    public void setHttpConfiguration(HttpConfiguration httpConfiguration) {
        this.httpConfiguration = httpConfiguration;
    }

    public boolean isMuteException() {
        return muteException;
    }

    /**
     * If enabled and an Exchange failed processing on the consumer side the response's body won't contain the
     * exception's stack trace.
     */
    public void setMuteException(boolean muteException) {
        this.muteException = muteException;
    }

    public boolean isAllowJavaSerializedObject() {
        return allowJavaSerializedObject;
    }

    /**
     * Whether to allow java serialization when a request uses context-type=application/x-java-serialized-object.
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming data from
     * the request to Java and that can be a potential security risk.
     */
    public void setAllowJavaSerializedObject(boolean allowJavaSerializedObject) {
        this.allowJavaSerializedObject = allowJavaSerializedObject;
    }

    public String getDeserializationFilter() {
        return deserializationFilter;
    }

    /**
     * Sets an {@link java.io.ObjectInputFilter} pattern (same syntax as {@code jdk.serialFilter}) applied when
     * deserializing Java objects from requests or responses with Content-Type
     * {@code application/x-java-serialized-object}, as a defense-in-depth measure on the opt-in
     * {@code allowJavaSerializedObject} / {@code transferException} path. When not set, the JVM-wide
     * {@code jdk.serialFilter} is used if present, otherwise a conservative default filter is applied.
     */
    public void setDeserializationFilter(String deserializationFilter) {
        this.deserializationFilter = deserializationFilter;
    }

}
