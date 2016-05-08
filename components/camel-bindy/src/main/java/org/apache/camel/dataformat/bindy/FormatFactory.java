/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dataformat.bindy;


import org.apache.camel.dataformat.bindy.format.factories.BigDecimalFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.BigDecimalPatternFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.BigIntegerFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.BooleanFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.ByteFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.BytePatternFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.CharacterFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.DateFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.DoubleFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.DoublePatternFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.EnumFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.FloatFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.FloatPatternFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.FormatFactories;
import org.apache.camel.dataformat.bindy.format.factories.IntegerFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.IntegerPatternFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.LocalDateFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.LocalDateTimeFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.LocalTimeFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.LongFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.LongPatternFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.ShortFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.ShortPatternFormatFactory;
import org.apache.camel.dataformat.bindy.format.factories.StringFormatFactory;


/**
 * Factory to return {@link Format} classes for a given type.
 */
public final class FormatFactory {

    private static final FormatFactory INSTANCE = new FormatFactory();

    static {
        FormatFactories.getInstance()
                .register(new StringFormatFactory())
                .register(new DateFormatFactory())
                .register(new BooleanFormatFactory())
                .register(new BigIntegerFormatFactory())
                .register(new LocalTimeFormatFactory())
                .register(new LocalDateTimeFormatFactory())
                .register(new LocalDateFormatFactory())
                .register(new CharacterFormatFactory())
                .register(new EnumFormatFactory())
                .register(new BigDecimalFormatFactory())
                .register(new BigDecimalPatternFormatFactory())
                .register(new DoubleFormatFactory())
                .register(new DoublePatternFormatFactory())
                .register(new FloatFormatFactory())
                .register(new FloatPatternFormatFactory())
                .register(new LongFormatFactory())
                .register(new LongPatternFormatFactory())
                .register(new IntegerFormatFactory())
                .register(new IntegerPatternFormatFactory())
                .register(new ShortFormatFactory())
                .register(new ShortPatternFormatFactory())
                .register(new ByteFormatFactory())
                .register(new BytePatternFormatFactory());
    }

    private FormatFactory() {
    }

    public static FormatFactory getInstance() {
        return INSTANCE;
    }

    private Format<?> doGetFormat(FormattingOptions formattingOptions) {
        return FormatFactories.getInstance().build(formattingOptions);
    }

    /**
     * Retrieves the format to use for the given type*
     */
    public Format<?> getFormat(FormattingOptions formattingOptions) throws Exception {
        if (formattingOptions.getBindyConverter() != null) {
            return formattingOptions.getBindyConverter().value().newInstance();
        }

        return doGetFormat(formattingOptions);
    }

}
