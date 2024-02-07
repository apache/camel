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
package org.apache.camel.language.wasm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import com.dylibso.chicory.runtime.Module;
import com.fasterxml.jackson.databind.node.BooleanNode;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.ExpressionResultTypeAware;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.wasm.WasmFunction;
import org.apache.camel.wasm.WasmSupport;

public class WasmExpression extends ExpressionAdapter implements ExpressionResultTypeAware {
    private final String expression;
    private String module;

    private String resultTypeName;
    private Class<?> resultType;

    private String headerName;
    private String propertyName;

    private TypeConverter typeConverter;
    private WasmFunction function;

    public WasmExpression(String expression) {
        this.expression = expression;
    }

    @Override
    public void init(CamelContext camelContext) {
        super.init(camelContext);

        if (module == null) {
            throw new IllegalArgumentException("Module must be configured");
        }

        this.typeConverter = camelContext.getTypeConverter();

        if (resultTypeName != null && (resultType == null || resultType == Object.class)) {
            resultType = camelContext.getClassResolver().resolveClass(resultTypeName);
        }
        if (resultType == null || resultType == Object.class) {
            resultType = byte[].class;
        }

        final ResourceLoader rl = PluginHelper.getResourceLoader(camelContext);
        final Resource res = rl.resolveResource(module);

        try (InputStream is = res.getInputStream()) {
            this.function = new WasmFunction(Module.builder(is).build(), expression);
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public String getExpressionText() {
        return this.expression;
    }

    @Override
    public Class<?> getResultType() {
        return this.resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of the header to use as input instead of the message body.
     * </p>
     * It has higher precedence than the propertyName if both are set.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Name of the property to use as input instead of the message body.
     * </p>
     * It has lower precedence than the headerName if both are set.
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getModule() {
        return module;
    }

    /**
     * Set the module (the distributable, loadable, and executable unit of code in WebAssembly) resource that provides
     * the expression function.
     */
    public void setModule(String module) {
        this.module = module;
    }

    @Override
    public boolean matches(Exchange exchange) {
        final Object value = evaluate(exchange, Object.class);

        if (value instanceof BooleanNode) {
            return ((BooleanNode) value).asBoolean();
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }

        return false;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        try {
            byte[] in = WasmSupport.serialize(exchange);
            byte[] result = function.run(in);

            return this.typeConverter.convertTo(resultType, exchange, result);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }
}
