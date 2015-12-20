package org.apache.camel.component.sql.sspt;

import org.apache.camel.component.sql.sspt.ast.ParseException;
import org.apache.camel.component.sql.sspt.ast.Template;
import org.apache.camel.component.sql.sspt.parser.SSPTParser;

import javax.sql.DataSource;
import java.io.StringReader;

public class SimpleStoredProcedureFactory {


    public SimpleStoredProcedure createFromString(String string, DataSource dataSource) throws ParseException {
        Template sptpRootNode = parseTemplate(string);
        return new SimpleStoredProcedure(dataSource, sptpRootNode);

    }

    public Template parseTemplate(String template) throws ParseException {
        SSPTParser parser = new SSPTParser(new StringReader(template));

        return validate(parser.parse());
    }

    private Template validate(Template input) throws ParseException {
        if (input.getOutParameterList().isEmpty()) {
            throw new ParseException("At least one OUT parameter must be given.");
        }
        return input;
    }


}
