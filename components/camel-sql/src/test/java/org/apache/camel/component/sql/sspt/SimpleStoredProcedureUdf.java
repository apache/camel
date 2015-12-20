package org.apache.camel.component.sql.sspt;

/**
 * Created by snurmine on 12/20/15.
 */
public class SimpleStoredProcedureUdf {

    public static void addnumbers(int VALUE1, int VALUE2, int[] RESULT) {
        System.out.println("calling addnumbers:" + VALUE1 + "," + VALUE2);

        RESULT[0] = VALUE1 + VALUE2;


    }
}
