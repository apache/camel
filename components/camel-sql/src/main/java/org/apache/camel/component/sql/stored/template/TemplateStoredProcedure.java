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
package org.apache.camel.component.sql.stored.template;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.sql.stored.template.ast.InputParameter;
import org.apache.camel.component.sql.stored.template.ast.OutParameter;
import org.apache.camel.component.sql.stored.template.ast.Template;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.StoredProcedure;

public class TemplateStoredProcedure extends StoredProcedure {

    private final Template template;

    public TemplateStoredProcedure(JdbcTemplate jdbcTemplate, Template template) {
        this.template = template;
        setDataSource(jdbcTemplate.getDataSource());

        setSql(template.getProcedureName());

        for (InputParameter inputParameter : template.getInputParameterList()) {
            declareParameter(new SqlParameter(inputParameter.getName(), inputParameter.getSqlType()));
        }

        for (OutParameter outParameter : template.getOutParameterList()) {
            declareParameter(new SqlOutParameter(outParameter.getName(), outParameter.getSqlType()));
            setFunction(false);
        }

        compile();
    }

    public void execute(Exchange exchange) {

        Map<String, Object> params = new HashMap<>();

        for (InputParameter inputParameter : template.getInputParameterList()) {
            params.put(inputParameter.getName(), inputParameter.getValueExpression().evaluate(exchange, inputParameter.getJavaType()));
        }

        Map<String, Object> ret = super.execute(params);

        for (OutParameter out : template.getOutParameterList()) {
            exchange.getOut().setHeader(out.getOutHeader(), ret.get(out.getName()));
        }

    }

}
