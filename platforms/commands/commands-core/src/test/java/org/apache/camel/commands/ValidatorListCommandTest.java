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
package org.apache.camel.commands;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.SimpleBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.apache.camel.model.dataformat.StringDataFormat;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.transformer.CustomTransformerDefinition;
import org.apache.camel.model.transformer.DataFormatTransformerDefinition;
import org.apache.camel.model.transformer.EndpointTransformerDefinition;
import org.apache.camel.model.validator.CustomValidatorDefinition;
import org.apache.camel.model.validator.EndpointValidatorDefinition;
import org.apache.camel.model.validator.PredicateValidatorDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.Validator;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ValidatorListCommandTest {

    private static final Logger LOG = LoggerFactory.getLogger(ValidatorListCommandTest.class);

    @Test
    public void testValidatorList() throws Exception {
        String out = doTest(false);
        assertTrue(out.contains("xml:foo"));
        assertTrue(out.contains("java:" + this.getClass().getName()));
        assertTrue(out.contains("custom"));
        assertTrue(out.contains("Started"));
        assertFalse(out.contains("ProcessorValidator["));
        assertFalse(out.contains("processor='validate(body)'"));
        assertFalse(out.contains("processor='sendTo(direct://validator)'"));
        assertFalse(out.contains("MyValidator["));
    }
    
    @Test
    public void testValidatorListVerbose() throws Exception {
        String out = doTest(true);
        assertTrue(out.contains("xml:foo"));
        assertTrue(out.contains("java:" + this.getClass().getName()));
        assertTrue(out.contains("custom"));
        assertTrue(out.contains("Started"));
        assertTrue(out.contains("ProcessorValidator["));
        assertTrue(out.contains("processor='validate(body)'"));
        assertTrue(out.contains("processor='sendTo(direct://validator)'"));
        assertTrue(out.contains("MyValidator["));
    }
    
    private String doTest(boolean verbose) throws Exception {
        CamelContext context = new DefaultCamelContext();
        EndpointValidatorDefinition evd = new EndpointValidatorDefinition();
        evd.setType("xml:foo");
        evd.setUri("direct:validator");
        context.getValidators().add(evd);
        PredicateValidatorDefinition pvd = new PredicateValidatorDefinition();
        pvd.setType(this.getClass());
        pvd.setExpression(new ExpressionDefinition(ExpressionBuilder.bodyExpression()));
        context.getValidators().add(pvd);
        CustomValidatorDefinition cvd = new CustomValidatorDefinition();
        cvd.setType("custom");
        cvd.setClassName(MyValidator.class.getName());
        context.getValidators().add(cvd);
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("foobar"));
        context.start();

        CamelController controller = new DummyCamelController(context);

        OutputStream os = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(os);

        ValidatorListCommand command = new ValidatorListCommand(null, false, verbose, false);
        command.execute(controller, ps, null);

        String out = os.toString();
        assertNotNull(out);
        LOG.info("\n\n{}\n", out);

        context.stop();
        return out;
    }

    public static class MyValidator extends Validator {
        @Override
        public void validate(Message message, DataType type) throws ValidationException {
            return;
        }
    }
}

