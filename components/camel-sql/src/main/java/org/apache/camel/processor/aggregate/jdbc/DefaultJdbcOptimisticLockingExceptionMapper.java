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
package org.apache.camel.processor.aggregate.jdbc;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.util.ObjectHelper;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * A default {@link JdbcOptimisticLockingExceptionMapper} which checks the caused exception (and its nested)
 * whether any of them is a constraint violation exception.
 * <p/>
 * The following check is done:
 * <ul>
 *     <li>If the caused exception is an {@link SQLException}</li> then the SQLState is checked if starts with <tt>23</tt>.
 *     <li>If the caused exception is a {@link DataIntegrityViolationException}</li>
 *     <li>If the caused exception class name has <tt>ConstraintViolation</tt></li> in its name.
 *     <li>optional checking for FQN class name matches if any class names has been configured</li>
 * </ul>
 * In addition you can add FQN classnames using the {@link #addClassName(String)} or {@link #setClassNames(java.util.Set)}
 * methods. These class names is also matched. This allows to add vendor specific exception classes.
 */
public class DefaultJdbcOptimisticLockingExceptionMapper implements JdbcOptimisticLockingExceptionMapper {

    private final Set<String> classNames = new LinkedHashSet<>();

    @Override
    public boolean isOptimisticLocking(Exception cause) {
        Iterator<Throwable> it = ObjectHelper.createExceptionIterator(cause);
        while (it.hasNext()) {
            Throwable throwable = it.next();
            // if its a SQL exception
            if (throwable instanceof SQLException) {
                SQLException se = (SQLException) throwable;
                if (isConstraintViolation(se)) {
                    return true;
                }
            }
            if (throwable instanceof DataIntegrityViolationException) {
                return true;
            }

            // fallback to names
            String name = throwable.getClass().getName();
            if (name.contains("ConstraintViolation") || hasClassName(name)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isConstraintViolation(SQLException e) {
        return e.getSQLState().startsWith("23");
    }

    private boolean hasClassName(String name) {
        for (String className : classNames) {
            if (className.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void addClassName(String name) {
        classNames.add(name);
    }

    public void setClassNames(Set<String> names) {
        classNames.clear();
        classNames.addAll(names);
    }
}
