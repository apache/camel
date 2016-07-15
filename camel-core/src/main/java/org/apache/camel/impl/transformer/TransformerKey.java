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
package org.apache.camel.impl.transformer;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultEndpointRegistry;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ValueHolder;

/**
 * Key used in Transformer registry in {@link DefaultCamelContext},
 * to ensure a consistent lookup.
 */
public final class TransformerKey extends ValueHolder<String> {

    private String scheme;
    private DataType from;
    private DataType to;

    public TransformerKey(String scheme) {
        super(scheme);
        this.scheme = scheme;
        ObjectHelper.notEmpty(scheme, "scheme");
    }

    public TransformerKey(DataType from, DataType to) {
        super(createKeyString(from, to));
        this.from = from;
        this.to = to;
    }

    private static String createKeyString(DataType from, DataType to) {
        return from + "/" + to;
    }

    /**
     * Test if specified TransformerDefinition matches with data type represented by this key.
     * @param def TransformerDefinition
     * @return true if it matches, otherwise false
     */
    public boolean match(TransformerDefinition def) {
        if (scheme != null) {
            return scheme.equals(def.getScheme());
        }
        if (from == null) {
            return to.toString().equals(def.getTo());
        }
        if (to == null) {
            return from.toString().equals(def.getFrom());
        }
        return from.toString().equals(def.getFrom()) && to.toString().equals(def.getTo());
    }

    @Override
    public String toString() {
        return get();
    }

}
