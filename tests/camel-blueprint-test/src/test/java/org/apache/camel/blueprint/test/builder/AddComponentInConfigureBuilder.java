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

package org.apache.camel.blueprint.test.builder;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.timer.TimerComponent;
import org.apache.camel.model.ModelCamelContext;

public class AddComponentInConfigureBuilder extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        ModelCamelContext context = getContext();
        TimerComponent timerComponent = new TimerComponent();

        getContext().addComponent("my-timer", timerComponent);

        from("my-timer://test-timer?period=1000")
                .to("mock://result");
    }
}
