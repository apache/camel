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
package org.apache.camel.main;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.component.log.LogComponent;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25767: a properties file that changed creates the new beans in it, so a component that is configured in the
 * same reload can refer to them. A bean that exists already is left as it is, as components may hold it, and a change
 * to it in a properties file is logged as needing a restart.
 */
public class MainPropertiesReloadBeansTest {

    private static final String FORMATTER = "#class:org.apache.camel.support.processor.DefaultExchangeFormatter";

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private Appender appender;

    @BeforeEach
    public void captureWarnings() {
        appender = new AbstractAppender("reload-warnings", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getRootLogger().addAppender(appender, Level.ALL, null);
        context.updateLoggers();
    }

    @AfterEach
    public void stopCapture() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getRootLogger().removeAppender(appender.getName());
        context.updateLoggers();
        appender.stop();
    }

    @Test
    public void aNewBeanIsCreatedAndAComponentOfTheSameReloadRefersToIt() throws Exception {
        Main main = main(new Properties());
        try {
            main.start();
            CamelContext context = main.getCamelContext();

            // application.properties saved in dev mode: a new bean, and the log component configured with it
            reload(main).onChanged("file:application.properties", properties(
                    "camel.beans.myFormatter", FORMATTER,
                    "camel.beans.myFormatter.show-all", "true",
                    "camel.component.log.exchange-formatter", "#bean:myFormatter"));

            DefaultExchangeFormatter bean
                    = context.getRegistry().lookupByNameAndType("myFormatter", DefaultExchangeFormatter.class);
            assertThat(bean).isNotNull();
            assertThat(bean.isShowAll()).isTrue();
            assertThat(context.getComponent("log", LogComponent.class).getExchangeFormatter()).isSameAs(bean);
            assertThat(warnings).noneMatch(w -> w.contains("restart"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void aBeanThatExistsIsLeftAsItIsAndNeedsARestart() throws Exception {
        Main main = main(properties("camel.beans.myFormatter", FORMATTER));
        try {
            main.start();
            CamelContext context = main.getCamelContext();
            DefaultExchangeFormatter bean
                    = context.getRegistry().lookupByNameAndType("myFormatter", DefaultExchangeFormatter.class);

            reload(main).onChanged("file:application.properties", properties("camel.beans.myFormatter.show-all", "true"));

            assertThat(context.getRegistry().lookupByName("myFormatter")).isSameAs(bean);
            assertThat(bean.isShowAll()).isFalse();
            assertThat(warnings).anyMatch(w -> w.contains("myFormatter") && w.contains("restart"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void aContextReloadLeavesABeanThatExistsWithoutAWarning() throws Exception {
        Main main = main(properties("camel.beans.myFormatter", FORMATTER));
        try {
            main.start();
            CamelContext context = main.getCamelContext();
            DefaultExchangeFormatter bean
                    = context.getRegistry().lookupByNameAndType("myFormatter", DefaultExchangeFormatter.class);

            // a context reload applies the options with a property placeholder again (secret rotation)
            reload(main).onReload("ContextReload", properties("camel.beans.myFormatter.show-all", "true"));

            assertThat(context.getRegistry().lookupByName("myFormatter")).isSameAs(bean);
            assertThat(warnings).noneMatch(w -> w.contains("restart"));
        } finally {
            main.stop();
        }
    }

    private static Main main(Properties properties) {
        Main main = new Main();
        main.setDefaultPropertyPlaceholderLocation("false");
        main.setOverrideProperties(properties);
        return main;
    }

    private static MainPropertiesReload reload(Main main) {
        MainPropertiesReload reload = new MainPropertiesReload(main);
        reload.setCamelContext(main.getCamelContext());
        return reload;
    }

    private static OrderedLocationProperties properties(String... keysAndValues) {
        OrderedLocationProperties answer = new OrderedLocationProperties();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            answer.put("file:application.properties", keysAndValues[i], keysAndValues[i + 1]);
        }
        return answer;
    }
}
