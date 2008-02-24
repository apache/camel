/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dataformat.supercsv;

import java.beans.PropertyDescriptor;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.springframework.beans.BeanUtils;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;

/**
 * @version $Revision$
 */
public class CsvDataFormat implements DataFormat {
    private CsvPreference preference = CsvPreference.STANDARD_PREFERENCE;

    public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws Exception {
        OutputStreamWriter out = new OutputStreamWriter(outputStream);
        if (object instanceof Map) {
            ICsvMapWriter writer = new CsvMapWriter(out, preference);
            Map<String, ? extends Object> map = (Map<String, ? extends Object>) object;

            Set<String> keys = map.keySet();
            String[] headers = new String[keys.size()];
            keys.toArray(headers);
            writer.writeHeader(headers);
            writer.write(map, headers);
        }
        else if (object != null) {
            ICsvBeanWriter writer = new CsvBeanWriter(out, preference);
            PropertyDescriptor[] properties = BeanUtils.getPropertyDescriptors(object.getClass());
            int size = properties.length;
            String[] headers = new String[size];
            for (int i = 0; i < size; i++) {
                headers[i] = properties[i].getName();
            }
            writer.writeHeader(headers);
            writer.write(object, headers);
        }
        out.close();
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        ICsvMapReader reader = new CsvMapReader(new InputStreamReader(inputStream), preference);
        String[] headers = reader.getCSVHeader(true);
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        try {
            while (true) {
                Map<String, String> map = reader.read(headers);
                if (map == null) {
                    break;
                }
                else {
                    list.add(map);
                }
            }
        }
        catch (EOFException e) {
            // ignore exception
        }
        catch (IOException e) {
            throw e;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        else {
            return list;
        }
    }

    public CsvPreference getPreference() {
        return preference;
    }

    public void setPreference(CsvPreference preference) {
        this.preference = preference;
    }
}
