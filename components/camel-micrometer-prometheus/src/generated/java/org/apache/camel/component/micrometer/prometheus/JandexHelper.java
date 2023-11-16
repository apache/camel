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
package org.apache.camel.component.micrometer.prometheus;

import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

import java.io.IOException;
import java.io.InputStream;

public final class JandexHelper {

    private JandexHelper() {
    }

    public static Index readJandexIndex(CamelContext camelContext) throws IOException {
        InputStream is = camelContext.getClassResolver().loadResourceAsStream("META-INF/micrometer-binder-index.dat");
        try {
            if (is != null) {
                IndexReader reader = new IndexReader(is);
                return reader.read();
            }
        } finally {
            IOHelper.close(is);
        }
        return null;
    }
}
