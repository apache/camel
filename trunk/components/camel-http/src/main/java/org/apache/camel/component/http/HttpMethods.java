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
package org.apache.camel.component.http;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.TraceMethod;

public enum HttpMethods implements Expression {

    GET(GetMethod.class), POST(PostMethod.class), PUT(PutMethod.class), DELETE(DeleteMethod.class), HEAD(
        HeadMethod.class), OPTIONS(OptionsMethod.class), TRACE(TraceMethod.class);

    final Class<? extends HttpMethod> clazz;
    final boolean entity;

    HttpMethods(Class<? extends HttpMethod> clazz) {
        this.clazz = clazz;
        entity = EntityEnclosingMethod.class.isAssignableFrom(clazz);
    }

    public HttpMethod createMethod(String url) {
        try {
            return clazz.getDeclaredConstructor(String.class).newInstance(url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public final boolean isEntityEnclosing() {
        return entity;
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        return ExpressionBuilder.constantExpression(name()).evaluate(exchange, type);
    }

}
