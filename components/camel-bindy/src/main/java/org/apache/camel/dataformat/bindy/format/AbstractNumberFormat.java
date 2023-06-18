/*
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
package org.apache.camel.dataformat.bindy.format;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.apache.camel.dataformat.bindy.Format;

/**
 *
 */
public abstract class AbstractNumberFormat<T extends Number> implements Format<T> {
    private boolean impliedDecimalPosition;
    private int precision;
    private DecimalFormat format;
    private double multiplier;

    /**
     *
     */
    protected AbstractNumberFormat() {
        this(false, 0, null);
    }

    /**
     *
     */
    protected AbstractNumberFormat(boolean impliedDecimalPosition, int precision, Locale locale) {
        this.impliedDecimalPosition = impliedDecimalPosition;
        this.precision = precision > 0 ? precision : 0;
        this.format = null;
        this.multiplier = 1;

        this.format = new DecimalFormat();
        this.format.setGroupingUsed(false);
        this.format.setDecimalSeparatorAlwaysShown(false);

        if (locale != null) {
            this.format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(locale));
        }

        if (this.impliedDecimalPosition) {
            this.format.setMinimumFractionDigits(0);
            this.format.setMaximumFractionDigits(0);
            this.multiplier = Math.pow(10D, precision);
        } else {
            this.format.setMinimumFractionDigits(this.precision);
            this.format.setMaximumFractionDigits(this.precision);
        }
    }

    protected boolean hasImpliedDecimalPosition() {
        return this.impliedDecimalPosition;
    }

    protected int getPrecision() {
        return this.precision;
    }

    protected DecimalFormat getFormat() {
        return this.format;
    }

    protected double getMultiplier() {
        return multiplier;
    }
}
