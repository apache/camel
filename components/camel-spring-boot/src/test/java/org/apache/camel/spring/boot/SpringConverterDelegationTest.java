/**
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
package org.apache.camel.spring.boot;

import org.apache.camel.TypeConverter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = SpringConverterDelegationTest.class, properties = "camel.springboot.typeConversion=true")
public class SpringConverterDelegationTest extends Assert {

    @Autowired
    TypeConverter typeConverter;

    @Test
    public void shouldConvertUsingSpringConverter() {
        String result = typeConverter.convertTo(String.class, new Convertable());
        assertEquals("converted!", result);
    }

    @Configuration
    public static class Config {
        @Bean
        ConvertableConverter convertableConverter() {
            return new ConvertableConverter();
        }

    }

    public static class Convertable {
    }

    public static class ConvertableConverter implements Converter<Convertable, String> {
        @Override
        public String convert(Convertable source) {
            return "converted!";
        }
    }
}