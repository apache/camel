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

module.exports = {
  extractConstantDisplayName: (name) => {
    return name.split('#').pop().split('@').join('.')
  },

  extractConstantName: (name) => {
    return name.split('#').pop().split('@').shift()
  },

  extractHeadersClass: (name) => {
    return name.split('#').shift()
  },

  constantLink: (artifactId, name) => {
      try {
        return `https://javadoc.io/doc/org.apache.camel/${artifactId}/latest/`
          + module.exports.extractHeadersClass(name).split('.').join('/') + '.html#'
          + module.exports.extractConstantName(name)
          + '[`' + module.exports.extractConstantDisplayName(name) + '`]'
      } catch (e) {
        console.log('error', e)
        return e.msg()
      }
  },

  // the body types of the catalog (CAMEL-24961): one row per side of the bodyTypes section
  bodySide: (side) => {
    const sides = {
      consumerOut: 'Consumer',
      consumerReply: 'Consumer reply',
      producerIn: 'Producer input',
      producerOut: 'Producer output',
      marshalIn: 'Marshal input',
      marshalOut: 'Marshal output',
      unmarshalIn: 'Unmarshal input',
      unmarshalOut: 'Unmarshal output',
      expressionIn: 'Expression input',
      expressionOut: 'Expression result',
    }
    return `*${sides[side] || side}*`
  },

  // text, bytes or stream, with the Java type when the body is always of that type
  bodyCarriers: (value) => {
    const carriers = (value.carriers || []).map((carrier) => '`' + carrier + '`')
    const words = carriers.length === 0 ? '`unknown`'
      : carriers.length === 1 ? carriers[0]
        : carriers.slice(0, -1).join(', ') + ' or ' + carriers[carriers.length - 1]
    return words + (value.javaType ? ' (`' + value.javaType + '`)' : '') + (value.format ? ', as ' + value.format : '')
  },

  // the description, the bodies for other values of an option, and the options the body depends on
  bodyDescription: (value) => {
    // only what reads as an attribute reference ({body}, not {header.foo}, which keeps a backslash), and the cell separator
    const escape = (text) => text.replace(/\{(\w[\w-]*)\}/g, '\\{$1}').split('|').join('\\|')
    const parts = []
    if (value.description) {
      parts.push(escape(value.description) + (value.description.endsWith('.') ? '' : '.'))
    }
    if (value.byOption && value.byOption.length) {
      parts.push(value.byOption.map((option) => `* With \`${option.option}=${option.value}\`: `
        + module.exports.bodyCarriers(option)).join('\n'))
    }
    if (value.dependsOn && value.dependsOn.length) {
      parts.push('Not known when ' + value.dependsOn.map((option) => '`' + option + '`').join(' or ') + ' is set.')
    }
    return parts.join('\n\n')
  },
}
