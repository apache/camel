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
package org.apache.camel.example.telegram;

import org.apache.camel.CamelContext;
import org.apache.camel.StartupListener;
import org.apache.camel.example.telegram.usage.GetUpdatesUsage;
import org.apache.camel.example.telegram.usage.LiveLocationUsage;
import org.apache.camel.example.telegram.usage.SendMessageUsage;
import org.apache.camel.example.telegram.usage.SendVenueUsage;

public class TelegramExamplesRunner implements StartupListener {

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        System.out.println("Camel is started. Ready to run examples!");

        // Methods usage examples
        new SendMessageUsage().run(context);
        new LiveLocationUsage().run(context);
        new GetUpdatesUsage().run(context);
        new SendVenueUsage().run(context);
    }
}

