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
package org.apache.camel.component.rest.openapi;

import java.io.InputStream;
import java.util.stream.Stream;

import org.apache.camel.spi.ContentTypeAware;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestOpenApiHelperTest {

    @Test
    public void emptyHostParamsAreNotAllowed() {
        assertThrows(IllegalArgumentException.class,
                () -> RestOpenApiHelper.isHostParam(""));
    }

    @Test
    public void nonUriHostParametersAreNotAllowed() {
        assertThrows(IllegalArgumentException.class,
                () -> RestOpenApiHelper.isHostParam("carrot"));
    }

    @Test
    public void nullHostParamsAreNotAllowed() {
        assertThrows(IllegalArgumentException.class,
                () -> RestOpenApiHelper.isHostParam(null));
    }

    @Test
    public void shouldNiceify() {
        assertThat(RestOpenApiHelper.isHostParam("http://api.example.com")).isEqualTo("http://api.example.com");
    }

    @Test
    public void shouldReturnUriParameter() {
        assertThat(RestOpenApiHelper.isHostParam("http://api.example.com")).isEqualTo("http://api.example.com");
    }

    @ParameterizedTest
    @MethodSource("contentTypes")
    public void yamlResourceContentType(String contentType, boolean isValid) {
        Resource resource = new FakeContentTypeAwareResource("https", null, contentType);
        assertThat(RestOpenApiHelper.isYamlResource(resource)).isEqualTo(isValid);
    }

    @ParameterizedTest
    @MethodSource("resourceLocations")
    public void yamlResourceLocation(String scheme, String location, boolean isValid) {
        Resource resource = new FakeContentTypeAwareResource(scheme, location, null);
        assertThat(RestOpenApiHelper.isYamlResource(resource)).isEqualTo(isValid);
    }

    private static Stream<Arguments> contentTypes() {
        return Stream.of(
                Arguments.of("application/yaml; charset=UTF-8", true),
                Arguments.of("application/yml", true),
                Arguments.of("text/yaml", true),
                Arguments.of("text/yml", true),
                Arguments.of("text/x-yaml", true),
                Arguments.of("application/json", false),
                Arguments.of("", false),
                Arguments.of(null, false));
    }

    private static Stream<Arguments> resourceLocations() {
        return Stream.of(
                Arguments.of("https", "/api/openapi.yml", true),
                Arguments.of("https", "/api/openapi.yaml", true),
                Arguments.of("file", "/api/openapi.yaml", true),
                Arguments.of("file", "/api/openapi.yml", true),
                Arguments.of("file", "/api/openapi.YAML", true),
                Arguments.of("classpath", "/api/openapi.yaml", true),
                Arguments.of("classpath", "/api/openapi.yml", true),
                Arguments.of("classpath", "/api/openapi.txt", false),
                Arguments.of("classpath", "", false),
                Arguments.of("classpath", null, false));
    }

    static final class FakeContentTypeAwareResource extends ResourceSupport implements ContentTypeAware {
        private String contentType;

        FakeContentTypeAwareResource(String scheme, String location, String contentType) {
            super(scheme, location);
            setContentType(contentType);
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public InputStream getInputStream() {
            return null;
        }
    }
}
