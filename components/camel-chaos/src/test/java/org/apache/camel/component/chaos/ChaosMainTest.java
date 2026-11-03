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
package org.apache.camel.component.chaos;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.Main;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel-chaos with camel-main: the camel.chaos.* options, the profiles, and the insecure:dev marking of
 * camel.chaos.rules, which makes profile prod refuse to start with rules.
 */
class ChaosMainTest {

    private Main main;

    @AfterEach
    void stop() {
        if (main != null) {
            main.stop();
        }
    }

    private Main main() {
        main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target");
            }
        });
        return main;
    }

    private Exchange send() throws Exception {
        return main.getCamelTemplate().send("direct:start", e -> e.getMessage().setBody("x"));
    }

    @Test
    void profileDevSwitchesChaosOnWithTheRules() throws Exception {
        main().addInitialProperty("camel.main.profile", "dev");
        main.addInitialProperty("camel.chaos.rules", "endpoint=mock:target error message=boom");
        main.addInitialProperty("camel.chaos.seed", "5");
        main.start();

        ChaosManager chaos = ChaosManager.get(main.getCamelContext());
        assertThat(chaos).isNotNull();
        assertThat(chaos.getSeed()).isEqualTo(5);
        assertThat(send().getException()).isInstanceOf(ChaosException.class).hasMessage("boom");
    }

    @Test
    void profileProdRefusesToStartWithRules() {
        main().addInitialProperty("camel.main.profile", "prod");
        main.addInitialProperty("camel.chaos.rules", "endpoint=mock:target error");

        assertThatThrownBy(main::start)
                .hasMessageContaining("Security policy violations detected")
                .hasMessageContaining("camel.chaos.rules");
    }

    @Test
    void profileProdWithEnabledStaysOff() throws Exception {
        main().addInitialProperty("camel.main.profile", "prod");
        main.addInitialProperty("camel.chaos.enabled", "true");
        main.start();

        assertThat(ChaosManager.get(main.getCamelContext())).isNull();
        assertThat(main.getCamelContext().getCamelContextExtension().getSendHook()).isNull();
    }

    @Test
    void withoutProfileRulesAloneDoNothing() throws Exception {
        main().addInitialProperty("camel.chaos.rules", "endpoint=mock:target error");
        main.start();

        assertThat(ChaosManager.get(main.getCamelContext())).isNull();
        assertThat(send().getException()).isNull();
    }

    @Test
    void enabledWithoutProfile() throws Exception {
        main().addInitialProperty("camel.chaos.enabled", "true");
        main.addInitialProperty("camel.chaos.rules", "endpoint=mock:target drop");
        main.addInitialProperty("camel.chaos.maxDuration", "1m");
        main.start();

        ChaosManager chaos = ChaosManager.get(main.getCamelContext());
        assertThat(chaos).isNotNull();
        assertThat(send().getException()).isNull();
        assertThat(chaos.report().toText()).contains("r1 active: endpoint=mock:target drop", "fired 1 (send 1)");
    }

    @Test
    void optionsFromJava() throws Exception {
        main().configure().chaosConfig().withEnabled(true).withSeed(9L).withRules("endpoint=mock:target error");
        main.start();

        ChaosManager chaos = ChaosManager.get(main.getCamelContext());
        assertThat(chaos.getSeed()).isEqualTo(9);
        assertThat(send().getException()).isInstanceOf(ChaosException.class);
    }

    @Test
    void withTheJarCamelMainDoesNotWarnThatItIsMissing() throws Exception {
        String logger = BaseMainSupport.class.getName();
        List<String> warnings = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("ChaosMainTest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                warnings.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        LoggerConfig config = new LoggerConfig(logger, Level.WARN, true);
        config.addAppender(appender, Level.WARN, null);
        ctx.getConfiguration().addLogger(logger, config);
        ctx.updateLoggers();
        try {
            main().addInitialProperty("camel.chaos.enabled", "true");
            main.addInitialProperty("camel.chaos.rules", "endpoint=mock:target drop");
            main.start();

            assertThat(ChaosManager.get(main.getCamelContext())).isNotNull();
            assertThat(warnings).noneMatch(w -> w.contains("camel-chaos is not on the classpath"));
        } finally {
            ctx.getConfiguration().removeLogger(logger);
            ctx.updateLoggers();
            appender.stop();
        }
    }

    @Test
    void anUnknownOptionFailsTheStart() {
        main().addInitialProperty("camel.chaos.enabled", "true");
        main.addInitialProperty("camel.chaos.rule", "endpoint=mock:target error");

        assertThatThrownBy(main::start).hasStackTraceContaining("camel.chaos.rule");
    }
}
