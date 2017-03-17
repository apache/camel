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
package org.apache.camel.example;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class RestSwaggerApplication implements ApplicationRunner {

    @Autowired
    ApplicationContext context;

    @Value("${operation:getInventory}")
    String operation;

    @Value("${swagger:http://petstore.swagger.io/v2/swagger.json}")
    String specificationUri;

    @Autowired
    ProducerTemplate template;

    @Override
    public void run(final ApplicationArguments args) throws Exception {
        final Predicate<String> operations = "operation"::equals;

        final Map<String, Object> headers = args.getOptionNames().stream().filter(operations.negate())
            .collect(Collectors.toMap(identity(), arg -> args.getOptionValues(arg).get(0)));

        final String body = template.requestBodyAndHeaders("rest-swagger:" + specificationUri + "#" + operation, null,
            headers, String.class);

        System.out.println(body);

        SpringApplication.exit(context, () -> 0);
    }

    public static void main(final String[] args) {
        SpringApplication.run(RestSwaggerApplication.class, args);
    }

}
