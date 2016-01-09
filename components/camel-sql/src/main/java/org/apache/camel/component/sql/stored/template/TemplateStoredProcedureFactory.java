package org.apache.camel.component.sql.stored.template;

import org.apache.camel.component.sql.stored.template.ast.ParseRuntimeException;
import org.apache.camel.component.sql.stored.template.ast.Template;
import org.apache.camel.component.sql.stored.template.generated.ParseException;
import org.apache.camel.component.sql.stored.template.generated.SSPTParser;
import org.apache.camel.util.LRUCache;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.StringReader;

public class TemplateStoredProcedureFactory {

    final int TEMPLATE_CACHE_DEFAULT_SIZE = 200;

    final JdbcTemplate jdbcTemplate;

    LRUCache<String, TemplateStoredProcedure> templateCache = new LRUCache<String, TemplateStoredProcedure>(200);


    public TemplateStoredProcedureFactory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TemplateStoredProcedure createFromString(String string) {
        TemplateStoredProcedure fromCache = templateCache.get(string);

        if (fromCache != null) {
            return fromCache;
        }

        Template sptpRootNode = parseTemplate(string);
        TemplateStoredProcedure ret = new TemplateStoredProcedure(jdbcTemplate, sptpRootNode);

        templateCache.put(string, ret);

        return ret;

    }

    public Template parseTemplate(String template) {
        try {

            SSPTParser parser = new SSPTParser(new StringReader(template));
            return validate(parser.parse());
        } catch (ParseException parseException) {
            throw new ParseRuntimeException(parseException);
        }
    }

    private Template validate(Template input) {
        //TODO:remove validation ?
        return input;
    }


}
