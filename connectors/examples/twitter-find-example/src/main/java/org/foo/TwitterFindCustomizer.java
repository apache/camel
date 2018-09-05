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
package org.foo;

import org.apache.camel.component.connector.ConnectorCustomizer;
import org.apache.camel.util.ObjectHelper;
import org.foo.find.TwitterFindComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TwitterFindCustomizerProperties.class)
public class TwitterFindCustomizer implements ConnectorCustomizer<TwitterFindComponent> {
    @Autowired
    private TwitterFindCustomizerProperties configuration;

    @Override
    public void customize(TwitterFindComponent component) {
        if (ObjectHelper.isNotEmpty(configuration.getKeywords())) {
            component.addOption("keywords", configuration.getKeywords());
        }

        String keywords = (String)component.getOptions().get("keywords");
        String prefix = (String)component.getOptions().get("prefix");

        if (ObjectHelper.isNotEmpty(keywords) && ObjectHelper.isNotEmpty(prefix)) {
            component.addOption("keywords", prefix + keywords);
        }
    }
}
