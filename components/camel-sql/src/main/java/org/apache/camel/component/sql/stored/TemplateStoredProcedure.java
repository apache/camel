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
package org.apache.camel.component.sql.stored;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.sql.stored.template.ast.InOutParameter;
import org.apache.camel.component.sql.stored.template.ast.InParameter;
import org.apache.camel.component.sql.stored.template.ast.OutParameter;
import org.apache.camel.component.sql.stored.template.ast.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlInOutParameter;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.StoredProcedure;

public class TemplateStoredProcedure extends StoredProcedure {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateStoredProcedure.class);

    private final Template template;

    private final List<InParameter> inParameterList = new ArrayList<>();
    private final List<InOutParameter> inOutParameterList = new ArrayList<>();

    public TemplateStoredProcedure(JdbcTemplate jdbcTemplate, Template template, boolean function) {
        setJdbcTemplate(jdbcTemplate);
        this.template = template;
        setFunction(function);
        setSql(template.getProcedureName());

        for (Object parameter : template.getParameterList()) {
            if (parameter instanceof InParameter inputParameter) {
                SqlParameter sqlParameter;
                if (inputParameter.getScale() != null) {
                    sqlParameter = new SqlParameter(
                            inputParameter.getName(), inputParameter.getSqlType(), inputParameter.getScale());
                } else if (inputParameter.getTypeName() != null) {
                    sqlParameter = new SqlParameter(
                            inputParameter.getName(), inputParameter.getSqlType(), inputParameter.getTypeName());
                } else {
                    sqlParameter = new SqlParameter(inputParameter.getName(), inputParameter.getSqlType());
                }

                declareParameter(sqlParameter);
                inParameterList.add(inputParameter);
            } else if (parameter instanceof InOutParameter inOutParameter) {
                SqlInOutParameter sqlInOutParameter;
                if (inOutParameter.getScale() != null) {
                    sqlInOutParameter = new SqlInOutParameter(
                            inOutParameter.getOutValueMapKey(), inOutParameter.getSqlType(), inOutParameter.getScale());
                } else if (inOutParameter.getTypeName() != null) {
                    sqlInOutParameter = new SqlInOutParameter(
                            inOutParameter.getOutValueMapKey(), inOutParameter.getSqlType(), inOutParameter.getTypeName());
                } else {
                    sqlInOutParameter = new SqlInOutParameter(inOutParameter.getOutValueMapKey(), inOutParameter.getSqlType());
                }

                declareParameter(sqlInOutParameter);
                inOutParameterList.add(inOutParameter);
            } else if (parameter instanceof OutParameter outParameter) {
                SqlOutParameter sqlOutParameter;
                if (outParameter.getScale() != null) {
                    sqlOutParameter = new SqlOutParameter(
                            outParameter.getOutValueMapKey(), outParameter.getSqlType(), outParameter.getScale());
                } else if (outParameter.getTypeName() != null) {
                    sqlOutParameter = new SqlOutParameter(
                            outParameter.getOutValueMapKey(), outParameter.getSqlType(), outParameter.getTypeName());
                } else {
                    sqlOutParameter = new SqlOutParameter(outParameter.getOutValueMapKey(), outParameter.getSqlType());
                }

                declareParameter(sqlOutParameter);
            }
        }

        LOG.debug("Compiling stored procedure: {}", template.getProcedureName());
        compile();
    }

    public Map<String, Object> execute(Exchange exchange, Object rowData) {
        Map<String, Object> params = new HashMap<>();

        for (InParameter inParameter : inParameterList) {
            params.put(inParameter.getName(), inParameter.getValueExtractor().eval(exchange, rowData));
        }
        for (InOutParameter inOutParameter : inOutParameterList) {
            params.put(inOutParameter.getOutValueMapKey(), inOutParameter.getValueExtractor().eval(exchange, rowData));
        }

        LOG.debug("Invoking stored procedure: {}", template.getProcedureName());
        return super.execute(params);
    }

    public Template getTemplate() {
        return template;
    }
}
