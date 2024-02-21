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
 * Meta data for EIPs, components, data formats and other Camel concepts
 * <p/>
 * For example to associate labels to Camel components
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
public @interface Metadata {

    /**
     * A human display name of the parameter.
     * <p/>
     * This is used for documentation and tooling only.
     */
    String displayName() default "";

    /**
     * To define one or more labels.
     * <p/>
     * Multiple labels can be defined as a comma separated value.
     */
    String label() default "";

    /**
     * To define a default value.
     */
    String defaultValue() default "";

    /**
     * To define that this entity is required.
     */
    boolean required() default false;

    /**
     * An optional human readable title of this entity, to be used instead of a computed title.
     */
    String title() default "";

    /**
     * Returns a description of this entity.
     * <p/>
     * This is used for documentation and tooling only.
     */
    String description() default "";

    /**
     * Allows to define enums this options accepts.
     * <p/>
     * If the type is already an enum, then this option should not be used; instead you can use this option when the
     * type is a String that only accept certain values.
     * <p/>
     * Multiple values is separated by comma.
     */
    String enums() default "";

    /**
     * Whether the option is secret/sensitive information such as a password.
     */
    boolean secret() default false;

    /**
     * Whether to parameter can be configured as autowired
     * <p/>
     * This is used for automatic autowiring the option via its Java type, by looking up in the registry to find if
     * there is a single instance of matching type, which then gets configured. This can be used for automatic
     * configuring JDBC data sources, JMS connection factories, AWS Clients, etc.
     * <p/>
     * This is only supported on components, data formats, languages, etc; not on endpoints.
     */
    boolean autowired() default false;

    /**
     * To re-associate the preferred Java type of this parameter.
     * <p/>
     * This is used for parameters which are of a specialized type but can be configured by another Java type, such as
     * from a String.
     */
    String javaType() default "";

    /**
     * The first version this functionality was added to Apache Camel.
     */
    String firstVersion() default "";

    /**
     * Additional description that can explain the user about the deprecation and give reference to what to use instead.
     */
    String deprecationNote() default "";

    /**
     * Whether to skip this option
     */
    boolean skip() default false;

    /**
     * To exclude one or more properties.
     * <p/>
     * This is for example used when a Camel component extend another component, and then may need to not use some of
     * the properties from the parent component. Multiple properties can be separated by comma.
     */
    String excludeProperties() default "";

    /**
     * To include one or more properties.
     *
     * Some dataformats share same base but individually they have some specific options, then this can be used to
     * specify which options each implementation only supports.
     */
    String includeProperties() default "";

    /**
     * Indicates the list of schemes for which this metadata is applicable. This is used to filter out message headers
     * that are shared with several endpoints but only applicable for some of them.
     * <p/>
     * In the next example, the header {@code SOME_HEADER} is only applicable for endpoints whose scheme is "foo" or
     * "bar".
     *
     * <pre>
     * <code>
     *
     * &#64;Metadata(description = "some description", javaType = "String", applicableFor = {"foo", "bar"})
     * public static final String SOME_HEADER = "someHeaderName";
     * </code>
     * </pre>
     */
    String[] applicableFor() default {};

    /**
     * Whether the option can refer to a file by using file: or classpath: as prefix and specify the location of the
     * file.
     */
    boolean supportFileReference() default false;

    /**
     * Whether the option can be large input such as a SQL query, XSLT template, or scripting code.
     *
     * This can be used to help tooling to provide an input form instead of a single input field to give better user
     * experience.
     */
    boolean largeInput() default false;

    /**
     * If the option is some specific language such as SQL, XSLT, XML, JavaScript or something else.
     *
     * This can be used to help tooling to provide a better user experience.
     */
    String inputLanguage() default "";
}
