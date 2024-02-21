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
package org.apache.camel.language;

import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.spi.Registry;

/**
 * Ensures that the "bean" language is compliant with the typed language expectations.
 */
class BeanLanguageTest extends AbstractTypedLanguageTest<MethodCallExpression.Builder, MethodCallExpression> {

    BeanLanguageTest() {
        super(null, factory -> factory.bean().ref("someBean"));
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("someBean", new SomeBean());
        return registry;
    }

    public static class SomeBean {
        public Object echo(Object body) {
            return body;
        }
    }
}
