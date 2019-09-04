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
package org.apache.camel.component.spring.ws.converter;

import org.apache.camel.Converter;
import org.apache.camel.util.xml.StringSource;

/**
 * A helper class to transform to and from {@link org.springframework.xml.transform.StringSource} implementations
 * available in both Camel and Spring Webservices.
 * <p/>
 * Rationale: most of the time this converter will not be used since both Camel
 * and Spring-WS use the {@Source} interface abstraction. There is
 * however a chance that you may end up with incompatible {@link org.springframework.xml.transform.StringSource}
 * implementations, this converter handles these (corner)cases.
 * <p/>
 * Note that conversion options are limited by Spring's {@link org.springframework.xml.transform.StringSource}
 * since it's the most simple one. It has just one constructor that accepts a
 * String as input.
 */
@Converter(generateLoader = true)
public final class StringSourceConverter {

    private StringSourceConverter() {
    }

    /**
     * Converts a Spring-WS {@link org.springframework.xml.transform.StringSource}
     * to a Camel {@link org.apache.camel.converter.jaxp.StringSource}
     */
    @Converter
    public static StringSource toStringSourceFromSpring(org.springframework.xml.transform.StringSource springStringSource) {
        return new StringSource(springStringSource.toString());
    }

    /**
     * Converts a Camel {@link org.apache.camel.converter.jaxp.StringSource}
     * to a Spring-WS {@link org.springframework.xml.transform.StringSource}
     */
    @Converter
    public static org.springframework.xml.transform.StringSource toStringSourceFromCamel(StringSource camelStringSource) {
        return new org.springframework.xml.transform.StringSource(camelStringSource.getText());
    }
}
