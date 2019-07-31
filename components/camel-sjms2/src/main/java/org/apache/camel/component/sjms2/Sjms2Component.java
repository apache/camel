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
package org.apache.camel.component.sjms2;

import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.spi.annotations.Component;

/**
 * The <a href="http://camel.apache.org/sjms">Simple JMS2</a> component.
 */
@Component("sjms2")
public class Sjms2Component extends SjmsComponent {

    public Sjms2Component() {
        super(SjmsEndpoint.class);
    }

    @Override
    protected SjmsEndpoint createSjmsEndpoint(String uri, String remaining) {
        return new Sjms2Endpoint(uri, this, remaining);
    }
}
