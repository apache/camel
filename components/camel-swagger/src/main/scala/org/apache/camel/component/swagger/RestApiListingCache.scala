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
package org.apache.camel.component.swagger

import com.wordnik.swagger.core.util.ReaderUtil
import com.wordnik.swagger.config.SwaggerConfig
import com.wordnik.swagger.model.ApiListing

import org.apache.camel.model.rest.RestDefinition

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


/**
 * To cache the RestSwaggerReader
 */
object RestApiListingCache extends ReaderUtil {

  var cache: Option[Map[String, ApiListing]] = None
  val reader = new RestSwaggerReader()

  def listing(rests: mutable.Buffer[RestDefinition], config: SwaggerConfig): Option[Map[String, ApiListing]] = {
    cache.orElse {
      val listings = new ListBuffer[ApiListing]

      for (rest <- rests) {
        val some = reader.read(rest, config)
        if (!some.isEmpty) {
          listings += some.get
        }
      }

      if (listings.size > 0) {
        val mergedListings = groupByResourcePath(listings.toList)
        cache = Some(mergedListings.map(m => (m.resourcePath, m)).toMap)
      }
      cache
    }
  }

}
