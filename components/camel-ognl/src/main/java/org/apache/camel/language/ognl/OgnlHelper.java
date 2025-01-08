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
package org.apache.camel.language.ognl;

import ognl.Node;
import ognl.OgnlContext;
import ognl.OgnlException;

public final class OgnlHelper {

    private OgnlHelper() {
    }

    public static Object getValue(Object expression, OgnlContext ognlContext, Object root) throws OgnlException {
        // workaround bug in ognl 3.4.5
        Node node = (Node) expression;
        if (node.getAccessor() != null) {
            return node.getAccessor().get(ognlContext, root);
        } else {
            return node.getValue(ognlContext, root);
        }
    }
}
