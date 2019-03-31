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
package org.apache.camel.component.mustache;

import java.util.Map;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link MustacheEndpoint}.
 * URI pattern: {@code mustache://template_name.mustache}
 * Supports parameters:
 * <ul>
 * <li>encoding: default platform one </li>
 * <li>startDelimiter: default "{{" </li>
 * <li>endDelimiter: default "}}" </li>
 * </li>
 */
@Component("mustache")
public class MustacheComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private MustacheFactory mustacheFactory = new DefaultMustacheFactory();

    public MustacheComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MustacheEndpoint endpoint = new MustacheEndpoint(uri, this, remaining);
        endpoint.setMustacheFactory(getMustacheFactory());
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public MustacheFactory getMustacheFactory() {
        return mustacheFactory;
    }

    /**
     * To use a custom {@link MustacheFactory}
     */
    public void setMustacheFactory(MustacheFactory mustacheFactory) {
        this.mustacheFactory = mustacheFactory;
    }

}
