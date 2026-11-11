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
package org.apache.camel.component.jackson;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Stands in for the jackson data format of the Jackson 2.x line (camel-jackson), so the resolver can be tested with
 * both lines on the classpath. The name is the one the resolver looks up.
 */
public class JacksonDataFormat extends ServiceSupport implements DataFormat {

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) {
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) {
        return null;
    }
}
