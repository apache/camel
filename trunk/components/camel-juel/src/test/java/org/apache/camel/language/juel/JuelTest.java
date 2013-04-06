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
package org.apache.camel.language.juel;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import de.odysseus.el.ExpressionFactoryImpl;
import de.odysseus.el.util.SimpleContext;

import org.junit.Assert;
import org.junit.Test;


/**
 * @version 
 */
public class JuelTest extends Assert {

    @Test
    public void testJuel() throws Exception {
        ExpressionFactory factory = new ExpressionFactoryImpl();
        ELContext context  = new SimpleContext();
        ValueExpression valueExpression = factory.createValueExpression(context, "${123 * 2}", Object.class);
        Object value = valueExpression.getValue(context);

        assertEquals("Result is a Long object", 246L, value);
    }
}
