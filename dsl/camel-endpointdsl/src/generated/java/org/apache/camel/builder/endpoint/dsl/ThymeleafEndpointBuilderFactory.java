/* Generated by camel build tools - do NOT edit this file! */
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
package org.apache.camel.builder.endpoint.dsl;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;
import javax.annotation.processing.Generated;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.builder.endpoint.AbstractEndpointBuilder;

/**
 * Transform messages using a Thymeleaf template.
 * 
 * Generated by camel build tools - do NOT edit this file!
 */
@Generated("org.apache.camel.maven.packaging.EndpointDslMojo")
public interface ThymeleafEndpointBuilderFactory {

    /**
     * Builder for endpoint for the Thymeleaf component.
     */
    public interface ThymeleafEndpointBuilder
            extends
                EndpointProducerBuilder {
        default AdvancedThymeleafEndpointBuilder advanced() {
            return (AdvancedThymeleafEndpointBuilder) this;
        }

        /**
         * Sets whether the context map should allow access to all details. By
         * default only the message body and headers can be accessed. This
         * option can be enabled for full access to the current Exchange and
         * CamelContext. Doing so impose a potential security risk as this opens
         * access to the full power of CamelContext API.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param allowContextMapAll the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder allowContextMapAll(boolean allowContextMapAll) {
            doSetProperty("allowContextMapAll", allowContextMapAll);
            return this;
        }
        /**
         * Sets whether the context map should allow access to all details. By
         * default only the message body and headers can be accessed. This
         * option can be enabled for full access to the current Exchange and
         * CamelContext. Doing so impose a potential security risk as this opens
         * access to the full power of CamelContext API.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param allowContextMapAll the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder allowContextMapAll(String allowContextMapAll) {
            doSetProperty("allowContextMapAll", allowContextMapAll);
            return this;
        }
        /**
         * Whether to allow to use resource template from header or not (default
         * false). Enabling this allows to specify dynamic templates via message
         * header. However this can be seen as a potential security
         * vulnerability if the header is coming from a malicious user, so use
         * this with care.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param allowTemplateFromHeader the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder allowTemplateFromHeader(boolean allowTemplateFromHeader) {
            doSetProperty("allowTemplateFromHeader", allowTemplateFromHeader);
            return this;
        }
        /**
         * Whether to allow to use resource template from header or not (default
         * false). Enabling this allows to specify dynamic templates via message
         * header. However this can be seen as a potential security
         * vulnerability if the header is coming from a malicious user, so use
         * this with care.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param allowTemplateFromHeader the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder allowTemplateFromHeader(String allowTemplateFromHeader) {
            doSetProperty("allowTemplateFromHeader", allowTemplateFromHeader);
            return this;
        }
        /**
         * Whether templates have to be considered cacheable or not.
         * 
         * The option is a: <code>java.lang.Boolean</code> type.
         * 
         * Group: producer
         * 
         * @param cacheable the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder cacheable(Boolean cacheable) {
            doSetProperty("cacheable", cacheable);
            return this;
        }
        /**
         * Whether templates have to be considered cacheable or not.
         * 
         * The option will be converted to a <code>java.lang.Boolean</code>
         * type.
         * 
         * Group: producer
         * 
         * @param cacheable the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder cacheable(String cacheable) {
            doSetProperty("cacheable", cacheable);
            return this;
        }
        /**
         * The cache Time To Live for templates, expressed in milliseconds.
         * 
         * The option is a: <code>java.lang.Long</code> type.
         * 
         * Group: producer
         * 
         * @param cacheTimeToLive the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder cacheTimeToLive(Long cacheTimeToLive) {
            doSetProperty("cacheTimeToLive", cacheTimeToLive);
            return this;
        }
        /**
         * The cache Time To Live for templates, expressed in milliseconds.
         * 
         * The option will be converted to a <code>java.lang.Long</code> type.
         * 
         * Group: producer
         * 
         * @param cacheTimeToLive the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder cacheTimeToLive(String cacheTimeToLive) {
            doSetProperty("cacheTimeToLive", cacheTimeToLive);
            return this;
        }
        /**
         * Whether a template resources will be checked for existence before
         * being returned.
         * 
         * The option is a: <code>java.lang.Boolean</code> type.
         * 
         * Group: producer
         * 
         * @param checkExistence the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder checkExistence(Boolean checkExistence) {
            doSetProperty("checkExistence", checkExistence);
            return this;
        }
        /**
         * Whether a template resources will be checked for existence before
         * being returned.
         * 
         * The option will be converted to a <code>java.lang.Boolean</code>
         * type.
         * 
         * Group: producer
         * 
         * @param checkExistence the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder checkExistence(String checkExistence) {
            doSetProperty("checkExistence", checkExistence);
            return this;
        }
        /**
         * The template mode to be applied to templates.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Default: HTML
         * Group: producer
         * 
         * @param templateMode the value to set
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder templateMode(String templateMode) {
            doSetProperty("templateMode", templateMode);
            return this;
        }
    }

    /**
     * Advanced builder for endpoint for the Thymeleaf component.
     */
    public interface AdvancedThymeleafEndpointBuilder
            extends
                EndpointProducerBuilder {
        default ThymeleafEndpointBuilder basic() {
            return (ThymeleafEndpointBuilder) this;
        }

        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer (advanced)
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default AdvancedThymeleafEndpointBuilder lazyStartProducer(boolean lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer (advanced)
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default AdvancedThymeleafEndpointBuilder lazyStartProducer(String lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        /**
         * The character encoding to be used for reading template resources.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: advanced
         * 
         * @param encoding the value to set
         * @return the dsl builder
         */
        default AdvancedThymeleafEndpointBuilder encoding(String encoding) {
            doSetProperty("encoding", encoding);
            return this;
        }
        /**
         * The order in which this template will be resolved as part of the
         * resolver chain.
         * 
         * The option is a: <code>java.lang.Integer</code> type.
         * 
         * Group: advanced
         * 
         * @param order the value to set
         * @return the dsl builder
         */
        default AdvancedThymeleafEndpointBuilder order(Integer order) {
            doSetProperty("order", order);
            return this;
        }
        /**
         * The order in which this template will be resolved as part of the
         * resolver chain.
         * 
         * The option will be converted to a <code>java.lang.Integer</code>
         * type.
         * 
         * Group: advanced
         * 
         * @param order the value to set
         * @return the dsl builder
         */
        default AdvancedThymeleafEndpointBuilder order(String order) {
            doSetProperty("order", order);
            return this;
        }
        /**
         * An optional prefix added to template names to convert them into
         * resource names.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: advanced
         * 
         * @param prefix the value to set
         * @return the dsl builder
         */
        default AdvancedThymeleafEndpointBuilder prefix(String prefix) {
            doSetProperty("prefix", prefix);
            return this;
        }
        /**
         * The type of resolver to be used by the template engine.
         * 
         * The option is a:
         * <code>org.apache.camel.component.thymeleaf.ThymeleafResolverType</code> type.
         * 
         * Default: CLASS_LOADER
         * Group: advanced
         * 
         * @param resolver the value to set
         * @return the dsl builder
         */
        default AdvancedThymeleafEndpointBuilder resolver(org.apache.camel.component.thymeleaf.ThymeleafResolverType resolver) {
            doSetProperty("resolver", resolver);
            return this;
        }
        /**
         * The type of resolver to be used by the template engine.
         * 
         * The option will be converted to a
         * <code>org.apache.camel.component.thymeleaf.ThymeleafResolverType</code> type.
         * 
         * Default: CLASS_LOADER
         * Group: advanced
         * 
         * @param resolver the value to set
         * @return the dsl builder
         */
        default AdvancedThymeleafEndpointBuilder resolver(String resolver) {
            doSetProperty("resolver", resolver);
            return this;
        }
        /**
         * An optional suffix added to template names to convert them into
         * resource names.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: advanced
         * 
         * @param suffix the value to set
         * @return the dsl builder
         */
        default AdvancedThymeleafEndpointBuilder suffix(String suffix) {
            doSetProperty("suffix", suffix);
            return this;
        }
    }

    public interface ThymeleafBuilders {
        /**
         * Thymeleaf (camel-thymeleaf)
         * Transform messages using a Thymeleaf template.
         * 
         * Category: transformation
         * Since: 4.1
         * Maven coordinates: org.apache.camel:camel-thymeleaf
         * 
         * @return the dsl builder for the headers' name.
         */
        default ThymeleafHeaderNameBuilder thymeleaf() {
            return ThymeleafHeaderNameBuilder.INSTANCE;
        }
        /**
         * Thymeleaf (camel-thymeleaf)
         * Transform messages using a Thymeleaf template.
         * 
         * Category: transformation
         * Since: 4.1
         * Maven coordinates: org.apache.camel:camel-thymeleaf
         * 
         * Syntax: <code>thymeleaf:resourceUri</code>
         * 
         * Path parameter: resourceUri (required)
         * Path to the resource. You can prefix with: classpath, file, http,
         * ref, or bean. classpath, file and http loads the resource using these
         * protocols (classpath is default). ref will lookup the resource in the
         * registry. bean will call a method on a bean to be used as the
         * resource. For bean you can specify the method name after dot, eg
         * bean:myBean.myMethod.
         * This option can also be loaded from an existing file, by prefixing
         * with file: or classpath: followed by the location of the file.
         * 
         * @param path resourceUri
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder thymeleaf(String path) {
            return ThymeleafEndpointBuilderFactory.endpointBuilder("thymeleaf", path);
        }
        /**
         * Thymeleaf (camel-thymeleaf)
         * Transform messages using a Thymeleaf template.
         * 
         * Category: transformation
         * Since: 4.1
         * Maven coordinates: org.apache.camel:camel-thymeleaf
         * 
         * Syntax: <code>thymeleaf:resourceUri</code>
         * 
         * Path parameter: resourceUri (required)
         * Path to the resource. You can prefix with: classpath, file, http,
         * ref, or bean. classpath, file and http loads the resource using these
         * protocols (classpath is default). ref will lookup the resource in the
         * registry. bean will call a method on a bean to be used as the
         * resource. For bean you can specify the method name after dot, eg
         * bean:myBean.myMethod.
         * This option can also be loaded from an existing file, by prefixing
         * with file: or classpath: followed by the location of the file.
         * 
         * @param componentName to use a custom component name for the endpoint
         * instead of the default name
         * @param path resourceUri
         * @return the dsl builder
         */
        default ThymeleafEndpointBuilder thymeleaf(String componentName, String path) {
            return ThymeleafEndpointBuilderFactory.endpointBuilder(componentName, path);
        }

    }
    /**
     * The builder of headers' name for the Thymeleaf component.
     */
    public static class ThymeleafHeaderNameBuilder {
        /**
         * The internal instance of the builder used to access to all the
         * methods representing the name of headers.
         */
        private static final ThymeleafHeaderNameBuilder INSTANCE = new ThymeleafHeaderNameBuilder();

        /**
         * The name of the Thymeleaf template.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code ThymeleafResourceUri}.
         */
        public String thymeleafResourceUri() {
            return "CamelThymeleafResourceUri";
        }
        /**
         * The content of the Thymeleaf template.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code ThymeleafTemplate}.
         */
        public String thymeleafTemplate() {
            return "CamelThymeleafTemplate";
        }
        /**
         * The value of this header should be a Map with key/values that will be
         * override any existing key with the same name. This can be used to
         * preconfigure common key/values you want to reuse in your Thymeleaf
         * endpoints.
         * 
         * The option is a: {@code Map<String, Object>} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code ThymeleafVariableMap}.
         */
        public String thymeleafVariableMap() {
            return "CamelThymeleafVariableMap";
        }
        /**
         * The ServletContext for a web application.
         * 
         * The option is a: {@code jakarta.servlet.ServletContext} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code ThymeleafServletContext}.
         */
        public String thymeleafServletContext() {
            return "CamelThymeleafServletContext";
        }
    }
    static ThymeleafEndpointBuilder endpointBuilder(String componentName, String path) {
        class ThymeleafEndpointBuilderImpl extends AbstractEndpointBuilder implements ThymeleafEndpointBuilder, AdvancedThymeleafEndpointBuilder {
            public ThymeleafEndpointBuilderImpl(String path) {
                super(componentName, path);
            }
        }
        return new ThymeleafEndpointBuilderImpl(path);
    }
}