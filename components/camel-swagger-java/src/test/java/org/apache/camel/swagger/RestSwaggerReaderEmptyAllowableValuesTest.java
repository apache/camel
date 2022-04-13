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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Info;
import io.swagger.models.Swagger;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestSwaggerReaderEmptyAllowableValuesTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerReaderEmptyAllowableValuesTest.class);

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest("/rest").consumes("application/json")
                        .produces("text/plain").get("/test")
                        .responseMessage().code(200)
                        .message("Success")
                        .header("dummy_header1")
                        .description("dummy_description")
                        .endHeader().endResponseMessage()
                        .to("log:hi");
            }
        };
    }

    @Test
    public void testReaderRead() throws Exception {
        BeanConfig config = new BeanConfig();
        Info info = new Info();
        info.setVersion("1.0");
        info.setTitle("Test API");
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setInfo(info);
        RestSwaggerReader reader = new RestSwaggerReader();

        Swagger swagger
                = reader.read(context, context.getRestDefinitions(), config, context.getName(), new DefaultClassResolver());
        assertNotNull(swagger);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String json = mapper.writeValueAsString(swagger);
        LOG.info(json);

        assertTrue(!json.contains("\"enum\" : [ ]"));
        context.stop();
    }

}
