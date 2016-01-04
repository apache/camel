package org.apache.camel.component.sql.stored.template;

import org.apache.camel.component.sql.stored.template.ast.ParseRuntimeException;
import org.apache.camel.component.sql.stored.template.ast.Template;
import org.apache.camel.component.sql.stored.template.generated.ParseException;
import org.apache.camel.component.sql.stored.template.generated.SSPTParser;

import javax.sql.DataSource;
import java.io.StringReader;

public class TemplateStoredProcedureFactory {


    public TemplateStoredProcedure createFromString(String string, DataSource dataSource)  {
        Template sptpRootNode = parseTemplate(string);
        return new TemplateStoredProcedure(dataSource, sptpRootNode);

    }

    public Template parseTemplate(String template)  {
        try {
            SSPTParser parser = new SSPTParser(new StringReader(template));

            return validate(parser.parse());
        }catch(ParseException parseException) {
            throw new ParseRuntimeException(parseException);
        }
    }

    private Template validate(Template input) {
        //TODO:remove validation ?
        return input;
    }


}
