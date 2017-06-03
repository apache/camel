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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.apache.camel.model.dataformat.StringDataFormat;
import org.apache.camel.model.transformer.CustomTransformerDefinition;
import org.apache.camel.model.transformer.DataFormatTransformerDefinition;
import org.apache.camel.model.transformer.EndpointTransformerDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TransformerListCommandTest {

    private static final Logger LOG = LoggerFactory.getLogger(TransformerListCommandTest.class);

    @Test
    public void testTransformerList() throws Exception {
        String out = doTest(false);
        assertTrue(out.contains("xml:foo"));
        assertTrue(out.contains("json:bar"));
        assertTrue(out.contains("java:" + this.getClass().getName()));
        assertTrue(out.contains("xml:test"));
        assertTrue(out.contains("custom"));
        assertTrue(out.contains("Started"));
        assertFalse(out.contains("ProcessorTransformer["));
        assertFalse(out.contains("DataFormatTransformer["));
        assertFalse(out.contains("MyTransformer["));
    }
    
    @Test
    public void testTransformerListVerbose() throws Exception {
        String out = doTest(true);
        assertTrue(out.contains("xml:foo"));
        assertTrue(out.contains("json:bar"));
        assertTrue(out.contains("java:" + this.getClass().getName()));
        assertTrue(out.contains("xml:test"));
        assertTrue(out.contains("custom"));
        assertTrue(out.contains("Started"));
        assertTrue(out.contains("ProcessorTransformer["));
        assertTrue(out.contains("DataFormatTransformer["));
        assertTrue(out.contains("MyTransformer["));
    }
    
    private String doTest(boolean verbose) throws Exception {
        CamelContext context = new DefaultCamelContext();
        EndpointTransformerDefinition etd = new EndpointTransformerDefinition();
        etd.setFromType("xml:foo");
        etd.setToType("json:bar");
        etd.setUri("direct:transformer");
        context.getTransformers().add(etd);
        DataFormatTransformerDefinition dftd = new DataFormatTransformerDefinition();
        dftd.setFromType(this.getClass());
        dftd.setToType("xml:test");
        dftd.setDataFormatType(new StringDataFormat());
        context.getTransformers().add(dftd);
        CustomTransformerDefinition ctd = new CustomTransformerDefinition();
        ctd.setScheme("custom");
        ctd.setClassName(MyTransformer.class.getName());
        context.getTransformers().add(ctd);
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("foobar"));
        context.start();

        CamelController controller = new DummyCamelController(context);

        OutputStream os = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(os);

        TransformerListCommand command = new TransformerListCommand(null, false, verbose, false);
        command.execute(controller, ps, null);

        String out = os.toString();
        assertNotNull(out);
        LOG.info("\n\n{}\n", out);

        context.stop();
        return out;
    }

    public static class MyTransformer extends Transformer {
        @Override
        public void transform(Message message, DataType from, DataType to) throws Exception {
            return;
        }
    }
}

