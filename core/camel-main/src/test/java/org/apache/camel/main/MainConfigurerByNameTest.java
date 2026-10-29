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

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * camel.language.<name>.<option> and camel.dataformat.<name>.<option> are set with the configurer found by the name
 * (<name>-language, <name>-dataformat), not by reflection. The options of these test types have no setter, so only
 * their configurer can set them.
 */
public class MainConfigurerByNameTest {

    @Test
    public void testLanguageAndDataFormat() {
        Main main = new Main();
        main.bind("mylang", new MyLanguage());
        main.bind("mydf", new MyDataFormat());
        main.addProperty("camel.language.mylang.level", "3");
        main.addProperty("camel.dataformat.mydf.level", "5");
        main.start();
        try {
            CamelContext context = main.getCamelContext();
            assertEquals(3, ((MyLanguage) context.resolveLanguage("mylang")).getLevel());
            assertEquals(5, ((MyDataFormat) context.resolveDataFormat("mydf")).getLevel());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testConfigurerOfAnotherType() {
        // a bean with the name of the language whose type is not the one of the configurer: set by reflection
        Main main = new Main();
        main.bind("otherlang", new OtherLanguage());
        main.addProperty("camel.language.otherlang.level", "7");
        main.start();
        try {
            assertEquals(7, ((OtherLanguage) main.getCamelContext().resolveLanguage("otherlang")).getLevel());
        } finally {
            main.stop();
        }
    }

    public static class MyLanguage extends LanguageSupport {
        private int level;

        public int getLevel() {
            return level;
        }

        @Override
        public Predicate createPredicate(String expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression createExpression(String expression) {
            throw new UnsupportedOperationException();
        }
    }

    public static class MyLanguageConfigurer extends PropertyConfigurerSupport implements GeneratedPropertyConfigurer {
        @Override
        public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {
            if ("level".equalsIgnoreCase(name)) {
                ((MyLanguage) obj).level = property(camelContext, int.class, value);
                return true;
            }
            return false;
        }
    }

    public static class OtherLanguage extends LanguageSupport {
        private int level;

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        @Override
        public Predicate createPredicate(String expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression createExpression(String expression) {
            throw new UnsupportedOperationException();
        }
    }

    public static class MyDataFormat extends ServiceSupport implements DataFormat {
        private int level;

        public int getLevel() {
            return level;
        }

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) {
            throw new UnsupportedOperationException();
        }
    }

    public static class MyDataFormatConfigurer extends PropertyConfigurerSupport implements GeneratedPropertyConfigurer {
        @Override
        public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {
            if ("level".equalsIgnoreCase(name)) {
                ((MyDataFormat) obj).level = property(camelContext, int.class, value);
                return true;
            }
            return false;
        }
    }
}
