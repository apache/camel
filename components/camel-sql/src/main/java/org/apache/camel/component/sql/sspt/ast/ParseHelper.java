package org.apache.camel.component.sql.sspt.ast;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Types;


public class ParseHelper {

    public static int parseSqlType(String sqlType) {
        Field field = ReflectionUtils.findField(Types.class, sqlType);
        if (field == null) {
            throw new ParseException("Field " + sqlType + " not found from java.procedureName.Types");
        }
        try {
            return field.getInt(Types.class);
        } catch (IllegalAccessException e) {
            throw new ParseException(e);
        }
    }

    public static Class sqlTypeToJavaType(int sqlType, String sqlTypeStr) {
        //TODO: as rest of types.
        //TODO: add test for each type.
        Class ret = null;
        switch (sqlType) {
            case Types.INTEGER:
                ret = Integer.class;
                break;
            case Types.VARCHAR:
                ret = String.class;
                break;
            case Types.BIGINT:
                ret = BigInteger.class;
                break;
            case Types.CHAR:
                ret = String.class;
                break;
            case Types.BOOLEAN:
                ret = Boolean.class;
                break;
            case Types.DATE:
                ret = Date.class;
                break;
            case Types.TIMESTAMP:
                ret = Date.class;
                break;
        }
        if (ret == null) {
            throw new ParseException("Unable to map SQL type " + sqlTypeStr + " to Java type");

        }
        return ret;
    }
}
