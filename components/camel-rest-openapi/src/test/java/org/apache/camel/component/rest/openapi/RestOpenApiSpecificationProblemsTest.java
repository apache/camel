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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestOpenApiSpecificationProblemsTest {

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender
            = new AbstractAppender("SpecificationProblems", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel() == Level.WARN) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };
    private final CamelContext camelContext = new DefaultCamelContext();

    @BeforeEach
    void captureWarnings() {
        appender.start();
        endpointLogger().addAppender(appender);
        // addAppender creates a non-additive logger config, which would hide this logger from the root appenders
        endpointLogger().setAdditive(true);
    }

    @AfterEach
    void stopCapture() {
        endpointLogger().removeAppender(appender);
        appender.stop();
    }

    @Test
    void warnsAboutThePathTheParserIgnores() {
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "nested-path.json");

        // the parser ignores the path nested inside /stock, so there is no getStock operation
        assertThat(openApi.getPaths()).containsOnlyKeys("/stock");
        assertThat(warnings).singleElement().asString()
                .contains("nested-path.json")
                .contains("attribute paths.'/stock'./stock/{sku} is unexpected");
    }

    @Test
    void noWarningForValidSpecification() {
        RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "openapi-v3.json");

        assertThat(warnings).isEmpty();
    }

    @Test
    void errorSaysWhatTheParserCannotRead() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "openapi.json"));

        // openapi.json is a Swagger 2.0 document, which has no openapi attribute
        assertThat(e.getMessage()).contains("openapi.json").contains("attribute openapi is missing");
    }

    private static Logger endpointLogger() {
        return (Logger) LogManager.getLogger(RestOpenApiEndpoint.class);
    }
}
