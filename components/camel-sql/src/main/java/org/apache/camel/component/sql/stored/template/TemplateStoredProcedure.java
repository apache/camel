package org.apache.camel.component.sql.stored.template;

import org.apache.camel.Exchange;
import org.apache.camel.component.sql.stored.template.ast.InputParameter;
import org.apache.camel.component.sql.stored.template.ast.OutParameter;
import org.apache.camel.component.sql.stored.template.ast.Template;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.StoredProcedure;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;


public class TemplateStoredProcedure extends StoredProcedure {

    Template template;

    public TemplateStoredProcedure(DataSource dataSource, Template template) {
        this.template = template;
        setDataSource(dataSource);

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
