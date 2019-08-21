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
package org.apache.camel.component.bar;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.processor.RemoveHeaderProcessor;
import org.apache.camel.processor.SetHeaderProcessor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

public class BarSendDynamicAware implements SendDynamicAware {

    private String scheme;

    @Override
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) throws Exception {
        String query = StringHelper.after(uri, "?");
        if (query != null) {
            Map<String, String> map = new LinkedHashMap(URISupport.parseQuery(query));
            return new DynamicAwareEntry(uri, originalUri, map, null);
        } else {
            return new DynamicAwareEntry(uri, originalUri, null, null);
        }
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        if (entry.getProperties().containsKey("drink")) {
            Object value = entry.getProperties().get("drink");
            return new SetHeaderProcessor(ExpressionBuilder.constantExpression(BarConstants.DRINK), ExpressionBuilder.constantExpression(value));
        } else {
            return null;
        }
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        // remove header after use
        return new RemoveHeaderProcessor(BarConstants.DRINK);
    }

    @Override
    public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        // before the ?
        String uri = entry.getOriginalUri();
        return StringHelper.before(uri, "?");
    }
}
