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
package org.apache.camel.component.aws2.timestream;

import org.apache.camel.*;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "4.1.0", scheme = "aws2-timestream", title = "AWS Timestream",
             syntax = "aws2-timestream:clientType:label",
             producerOnly = true, category = { Category.CLOUD, Category.DATABASE },
             headersClass = Timestream2Constants.class)
public abstract class Timestream2AbstractEndpoint extends DefaultEndpoint {

    @UriParam
    private Timestream2Configuration configuration;

    public Timestream2AbstractEndpoint(String uri, Component component, Timestream2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Timestream2Component getComponent() {
        return (Timestream2Component) super.getComponent();
    }

    public Timestream2Configuration getConfiguration() {
        return configuration;
    }

}
