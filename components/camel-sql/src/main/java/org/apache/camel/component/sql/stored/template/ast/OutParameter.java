package org.apache.camel.component.sql.stored.template.ast;

/**
 * Created by snurmine on 12/20/15.
 */
public class OutParameter {


    String name;

    int sqlType;

    String outHeader;


    public OutParameter(String name, int sqlType, String outHeader) {
        this.name = name;
        this.sqlType = sqlType;
        this.outHeader = outHeader;
    }

    public String getName() {
        return name;
    }

    public int getSqlType() {
        return sqlType;
    }

    public String getOutHeader() {
        return outHeader;
    }
}
