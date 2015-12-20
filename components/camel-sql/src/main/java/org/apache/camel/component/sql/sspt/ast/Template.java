package org.apache.camel.component.sql.sspt.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * Root element of Simple Stored Procedure Template AST.
 */
public class Template {

    String procedureName;

    List<InputParameter> inputParameterList = new ArrayList<>();

    List<OutParameter> outParameterList = new ArrayList<>();

    public void addParameter(Object parameter) {

        if (parameter instanceof OutParameter) {
            outParameterList.add((OutParameter) parameter);
        } else {
            inputParameterList.add((InputParameter) parameter);

        }
    }

    public String getProcedureName() {
        return procedureName;
    }

    public void setProcedureName(String procedureName) {
        this.procedureName = procedureName;
    }

    public List<InputParameter> getInputParameterList() {
        return inputParameterList;
    }

    public List<OutParameter> getOutParameterList() {
        return outParameterList;
    }
}

