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
package org.apache.camel.component.mybatis.bean;

import java.util.List;

import org.apache.camel.component.mybatis.Account;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

public interface AccountService {

    @Select("select ACC_ID as id, ACC_FIRST_NAME as firstName, ACC_LAST_NAME as lastName"
        + ", ACC_EMAIL as emailAddress from ACCOUNT where ACC_ID = #{id}")
    Account selectBeanAccountById(@Param("id") int no);

    @Select("select * from ACCOUNT order by ACC_ID")
    @ResultMap("Account.AccountResult")
    List<Account> selectBeanAllAccounts();

    @Insert("insert into ACCOUNT (ACC_ID,ACC_FIRST_NAME,ACC_LAST_NAME,ACC_EMAIL)"
        + " values (#{id}, #{firstName}, #{lastName}, #{emailAddress})")
    void insertBeanAccount(Account account);

}
