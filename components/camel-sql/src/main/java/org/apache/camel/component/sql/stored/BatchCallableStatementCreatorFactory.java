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

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.sql.stored.template.ast.InParameter;
import org.apache.camel.component.sql.stored.template.ast.Template;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.CallableStatementCreatorFactory;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.StatementCreatorUtils;

public class BatchCallableStatementCreatorFactory {

    final CallableStatementCreatorFactory callableStatementCreatorFactory;

    final List<SqlParameter> sqlParameterList;

    final Template template;

    public BatchCallableStatementCreatorFactory(Template template) {
        this.template = template;
        this.sqlParameterList = createParams();
        this.callableStatementCreatorFactory = new CallableStatementCreatorFactory(formatSql(), createParams());
    }

    public void addParameter(CallableStatement callableStatement, Map<String, ?> batchRow) throws SQLException {
        int i = 1;
        for (SqlParameter parameter : getSqlParameterList()) {
            StatementCreatorUtils.setParameterValue(callableStatement, i, parameter, batchRow.get(parameter.getName()));
            i++;
        }
    }

    private String formatSql() {
        return "{call " + this.template.getProcedureName() + "(" + repeatParameter(this.template.getParameterList()
                .size())
               + ")}";
    }

    private String repeatParameter(int size) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < size; i++) {
            ret.append('?');
            if (i + 1 < size) {
                ret.append(',');
            }
        }
        return ret.toString();
    }

    private List<SqlParameter> createParams() {
        List<SqlParameter> params = new ArrayList<>();

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

                params.add(sqlParameter);

            } else {
                throw new UnsupportedOperationException("Only IN parameters supported by batch!");
            }
        }

        return params;
    }

    public CallableStatementCreator newCallableStatementCreator(Map<String, ?> params) {
        return this.callableStatementCreatorFactory.newCallableStatementCreator(params);
    }

    public List<SqlParameter> getSqlParameterList() {
        return sqlParameterList;
    }

    public Template getTemplate() {
        return template;
    }
}
