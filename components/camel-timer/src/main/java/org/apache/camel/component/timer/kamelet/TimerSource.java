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

package org.apache.camel.component.timer.kamelet;

import org.apache.camel.Exchange;
import org.apache.camel.builder.KameletRouteTemplate;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.spi.KameletProperty;
import org.apache.camel.spi.KameletSpec;

@KameletSpec(type = KameletSpec.KameletType.SOURCE,
             name = "timer-source",
             title = "Timer Source",
             description = "Produces periodic events with a custom payload.",
             supportLevel = KameletSpec.SupportLevel.STABLE,
             properties = {
                     @KameletProperty(
                                      name = "period",
                                      title = "Period",
                                      type = "integer",
                                      defaultValue = "1000",
                                      description = "The interval between two events in milliseconds."),
                     @KameletProperty(
                                      required = true,
                                      name = "message",
                                      title = "Message",
                                      example = "hello world",
                                      description = "The message to generate."),
                     @KameletProperty(
                                      name = "contentType",
                                      title = "Content Type",
                                      defaultValue = "text/plain",
                                      description = "The content type of the message being generated.")
             },
             namespace = "Scheduling",
             dependencies = { "camel:core", "camel:timer", "camel:kamelet" })
public class TimerSource extends KameletRouteTemplate {

    @Override
    protected void template(RouteTemplateDefinition template) {
        template.from("timer:tick?period={{period}}")
                .setBody(constant("{{message}}"))
                .setHeader(Exchange.CONTENT_TYPE, constant("{{contentType}}"))
                .to(kameletSink());
    }
}
