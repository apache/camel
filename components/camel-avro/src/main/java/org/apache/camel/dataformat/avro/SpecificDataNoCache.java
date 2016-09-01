package org.apache.camel.dataformat.avro;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;

/**
 * Created by pantinor on 31/08/16.
 */
public class SpecificDataNoCache extends SpecificData {

    public SpecificDataNoCache() {
        super();
    }

    public SpecificDataNoCache(ClassLoader classLoader) {
        super(classLoader);
    }

    public Object newRecord(Object old, Schema schema) {
        Class c = new SpecificDataNoCache().getClass(schema);
        return c == null ? super.newRecord(old, schema) : (c.isInstance(old) ? old : newInstance(c, schema));
    }

}
