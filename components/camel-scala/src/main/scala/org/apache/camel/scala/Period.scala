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
package org.apache.camel.scala

/**
 * Represents a period (expressed in milliseconds) in time
 */
abstract class Period {
  protected def ref: SimplePeriod

  def milliseconds: Long

  def ms = this

  def seconds = {
    ref.milliseconds *= 1000
    this
  }

  def seconds(other: Long): CompositePeriod = {
    seconds
    new CompositePeriod(this, new SimplePeriod(other))
  }

  def second = seconds

  def second(other: Long) = seconds(other)

  def minutes = {
    ref.milliseconds *= 60
    seconds
    this
  }

  def minutes(other: Long): CompositePeriod = {
    minutes
    new CompositePeriod(this, new SimplePeriod(other))
  }

  def minute = minutes

  def minute(other: Long) = minutes(other)

  def hours = {
    ref.milliseconds *= 60
    minutes
    this
  }

  def hours(other: Long): CompositePeriod = {
    hours
    new CompositePeriod(this, new SimplePeriod(other))
  }

  def hour = hours

  def hour(other: Long) = hours(other)

  override def equals(other: Any) = other.asInstanceOf[Period].milliseconds == milliseconds
}

/**
 * Single period is just a holder for milliseconds
 */
class SimplePeriod(var milliseconds: Long) extends Period {
  def ref = this
}

/**
 * Composite period is a combination of "left" and "right" period; it allows composing DSL like
 * "2 hours 15 minutes"
 */
class CompositePeriod(val left: Period, val right: SimplePeriod) extends Period {
  def ref = right

  def milliseconds = left.milliseconds + right.milliseconds
}
