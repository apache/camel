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
package org.apache.camel.component.bean;

import org.apache.camel.Consume;
import org.apache.camel.Header;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.language.Bean;
import org.apache.camel.language.Constant;

/**
 * Consumer using bean binding with an injected expressions such as: @Bean, @Constant etc.
 */
public class MyBeanBindingConsumer {

    private ProducerTemplate template;

    @Consume(uri = "direct:startBeanExpression")
    public void doSomethingBeanExpression(String payload, @Bean(ref = "myCounter") int count) {
        template.sendBodyAndHeader("mock:result", "Bye " + payload, "count", count);
    }

    @Consume(uri = "direct:startConstantExpression")
    public void doSomethingConstantExpression(String payload, @Constant("5") int count) {
        template.sendBodyAndHeader("mock:result", "Bye " + payload, "count", count);
    }

    @Consume(uri = "direct:startHeaderExpression")
    public void doSomethingHeaderExpression(String payload, @Header("number") int count) {
        template.sendBodyAndHeader("mock:result", "Bye " + payload, "count", count);
    }

    @Consume(uri = "direct:startMany")
    public void doSomethingManyExpression(String payload, @Constant("5") int count, @Header("number") int number) {
        template.sendBodyAndHeader("mock:result", "Bye " + payload, "count", count * number);
    }

    public void setTemplate(ProducerTemplate template) {
        this.template = template;
    }
}
