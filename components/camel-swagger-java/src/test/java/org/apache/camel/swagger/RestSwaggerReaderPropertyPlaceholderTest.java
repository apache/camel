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
package org.apache.camel.swagger;

import java.util.List;
import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Swagger;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.swagger.producer.DummyRestProducerFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Does not run well on CI due test uses JMX mbeans")
public class RestSwaggerReaderPropertyPlaceholderTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerReaderPropertyPlaceholderTest.class);

    @BindToRegistry("dummy-rest")
    private DummyRestProducerFactory factory = new DummyRestProducerFactory();

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties prop = new Properties();
        prop.put("foo", "hello");
        prop.put("bar", "bye");
        return prop;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest("/{{foo}}").consumes("application/json").produces("application/json").get("/hi/{name}").description("Saying hi").param().name("name").type(RestParamType.path)
                    .dataType("string").description("Who is it").endParam().to("log:hi").get("/{{bar}}/{name}").description("Saying bye").param().name("name")
                    .type(RestParamType.path).dataType("string").description("Who is it").endParam().responseMessage().code(200).message("A reply message").endResponseMessage()
                    .to("log:bye").post("/{{bar}}").description("To update the greeting message").consumes("application/xml").produces("application/xml").param().name("greeting")
                    .type(RestParamType.body).dataType("string").description("Message to use as greeting").endParam().to("log:bye");
            }
        };
    }

    @Test
    public void testReaderRead() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] {"http"});
        config.setBasePath("/api");
        RestSwaggerReader reader = new RestSwaggerReader();

        RestSwaggerSupport support = new RestSwaggerSupport();
        List<RestDefinition> rests = support.getRestDefinitions(context, context.getName());

        Swagger swagger = reader.read(rests, null, config, context.getName(), new DefaultClassResolver());
        assertNotNull(swagger);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String json = mapper.writeValueAsString(swagger);

        LOG.info(json);

        assertTrue(json.contains("\"host\" : \"localhost:8080\""));
        assertTrue(json.contains("\"basePath\" : \"/api\""));
        assertTrue(json.contains("\"/hello/bye\""));
        assertTrue(json.contains("\"summary\" : \"To update the greeting message\""));
        assertTrue(json.contains("\"/hello/bye/{name}\""));
        assertTrue(json.contains("\"/hello/hi/{name}\""));
        assertFalse(json.contains("{foo}"));
        assertFalse(json.contains("{bar}"));

        context.stop();
    }

}
