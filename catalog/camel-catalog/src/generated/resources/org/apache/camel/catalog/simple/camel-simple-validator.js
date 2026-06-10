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

// ============================================================================
// Apache Camel Simple Language Validator
//
// A self-contained JavaScript validator for the Camel Simple expression
// language. Works in any browser or Node.js — no Java backend needed.
//
// The catalog data (FUNCTIONS, OPERATORS) is AUTO-GENERATED from simple.json.
// The Maven build regenerates the catalog data section on each build.
// ============================================================================

// === AUTO-GENERATED CATALOG DATA — do not edit by hand ===

const FUNCTIONS = {
  'abs': true,
  'assert': true,
  'attachment': true,
  'attachments': true,
  'attachmentsContent': true,
  'attachmentsContentAs': true,
  'attachmentsContentAsText': true,
  'attachmentsHeader': true,
  'attachmentsHeaderAs': true,
  'attachmentsKeys': true,
  'attachmentsSize': true,
  'average': true,
  'base64Decode': true,
  'base64Encode': true,
  'bean': true,
  'body': true,
  'bodyAs': true,
  'bodyOneLine': true,
  'bodyType': true,
  'camelContext': true,
  'camelId': true,
  'capitalize': true,
  'ceil': true,
  'clearAttachments': true,
  'collate': true,
  'concat': true,
  'convertTo': true,
  'contains': true,
  'date': true,
  'date-with-timezone': true,
  'distinct': true,
  'empty': true,
  'env': true,
  'exchange': true,
  'exchangeId': true,
  'exchangeProperty': true,
  'exception': true,
  'filter': true,
  'forEach': true,
  'floor': true,
  'fromRouteId': true,
  'function': true,
  'hash': true,
  'header': true,
  'headerAs': true,
  'headers': true,
  'hostName': true,
  'htmlClean': true,
  'htmlDecode': true,
  'htmlParse': true,
  'id': true,
  'iif': true,
  'isAlpha': true,
  'isAlphaNumeric': true,
  'isEmpty': true,
  'isNumeric': true,
  'jq': true,
  'jsonpath': true,
  'join': true,
  'length': true,
  'list': true,
  'listAdd': true,
  'listRemove': true,
  'load': true,
  'lowercase': true,
  'mandatoryBodyAs': true,
  'map': true,
  'mapAdd': true,
  'mapRemove': true,
  'max': true,
  'messageAs': true,
  'messageHistory': true,
  'messageTimestamp': true,
  'min': true,
  'newEmpty': true,
  'normalizeWhitespace': true,
  'not': true,
  'null': true,
  'originalBody': true,
  'pad': true,
  'pretty': true,
  'prettyBody': true,
  'sort': true,
  'toPrettyJson': true,
  'toPrettyJsonBody': true,
  'toJson': true,
  'toJsonBody': true,
  'properties': true,
  'propertiesExist': true,
  'quote': true,
  'random': true,
  'range': true,
  'ref': true,
  'replace': true,
  'reverse': true,
  'routeGroup': true,
  'routeId': true,
  'safeQuote': true,
  'setVariable': true,
  'setHeader': true,
  'shuffle': true,
  'simpleJsonpath': true,
  'size': true,
  'skip': true,
  'split': true,
  'stepId': true,
  'substring': true,
  'substringAfter': true,
  'substringBefore': true,
  'substringBetween': true,
  'sum': true,
  'sys': true,
  'threadId': true,
  'threadName': true,
  'throwException': true,
  'trim': true,
  'type': true,
  'kindOfType': true,
  'unquote': true,
  'uppercase': true,
  'uuid': true,
  'val': true,
  'variable': true,
  'variableAs': true,
  'variables': true,
  'xpath': true
};

const OPERATORS = {
  '==': { kind: 'binary', description: 'Tests equality between left and right operand values. Camel will coerce the right operand type to match the left.' },
  '=~': { kind: 'binary', description: 'Tests equality between left and right operand values, ignoring case for string comparison.' },
  '>': { kind: 'binary', description: 'Tests whether the left operand is greater than the right operand.' },
  '>=': { kind: 'binary', description: 'Tests whether the left operand is greater than or equal to the right operand.' },
  '<': { kind: 'binary', description: 'Tests whether the left operand is less than the right operand.' },
  '<=': { kind: 'binary', description: 'Tests whether the left operand is less than or equal to the right operand.' },
  '!=': { kind: 'binary', description: 'Tests inequality between left and right operand values.' },
  '!=~': { kind: 'binary', description: 'Tests inequality between left and right operand values, ignoring case for string comparison.' },
  'contains': { kind: 'binary', description: 'Tests whether the left operand string contains the right operand string.' },
  '!contains': { kind: 'binary', description: 'Tests whether the left operand string does not contain the right operand string.' },
  '~~': { kind: 'binary', description: 'Tests whether the left operand string contains the right operand string, ignoring case.' },
  '!~~': { kind: 'binary', description: 'Tests whether the left operand string does not contain the right operand string, ignoring case.' },
  'regex': { kind: 'binary', description: 'Tests whether the left operand matches the right operand as a regular expression.' },
  '!regex': { kind: 'binary', description: 'Tests whether the left operand does not match the right operand as a regular expression.' },
  'in': { kind: 'binary', description: 'Tests whether the left operand is in a set of comma-separated values.' },
  '!in': { kind: 'binary', description: 'Tests whether the left operand is not in a set of comma-separated values.' },
  'is': { kind: 'binary', description: 'Tests whether the left operand is an instance of the right operand type (Java classname or short name).' },
  '!is': { kind: 'binary', description: 'Tests whether the left operand is not an instance of the right operand type.' },
  'range': { kind: 'binary', description: 'Tests whether the left operand is within the numeric range specified by \'from..to\'.' },
  '!range': { kind: 'binary', description: 'Tests whether the left operand is not within the numeric range specified by \'from..to\'.' },
  'startsWith': { kind: 'binary', description: 'Tests whether the left operand string starts with the right operand string.' },
  '!startsWith': { kind: 'binary', description: 'Tests whether the left operand string does not start with the right operand string.' },
  'endsWith': { kind: 'binary', description: 'Tests whether the left operand string ends with the right operand string.' },
  '!endsWith': { kind: 'binary', description: 'Tests whether the left operand string does not end with the right operand string.' },
  '++': { kind: 'unary', description: 'Increments the numeric value by one. Must immediately follow a function closing brace.' },
  '--': { kind: 'unary', description: 'Decrements the numeric value by one. Must immediately follow a function closing brace.' },
  '&&': { kind: 'logical', description: 'Logical AND. Both left and right predicates must evaluate to true.' },
  '||': { kind: 'logical', description: 'Logical OR. At least one of the left or right predicates must evaluate to true.' },
  '? :': { kind: 'ternary', description: 'Ternary conditional operator. Evaluates the predicate and returns trueValue if true, falseValue if false. Requires spaces around both ? and : tokens.' },
  '~>': { kind: 'chain', description: 'Pipes the result of the left expression as input body to the right expression. Use $param in the right expression to reference the piped value explicitly.' },
  '?~>': { kind: 'chain', description: 'Null-safe chain operator. Same as ~> but stops chaining and returns null if the left expression evaluates to null.' },
  '?:': { kind: 'other', description: 'Elvis operator (null-coalescing). Returns the left operand if it is not null/empty, otherwise returns the right operand as a fallback value.' }
};



// ========================================================================
// Tokenizer
// ========================================================================

function tokenize(input) {
  const tokens = [];
  let i = 0;
  const len = input.length;

  while (i < len) {
    // Function start: ${ or $simple{
    if (input[i] === '$' && i + 1 < len && input[i + 1] === '{') {
      tokens.push({ type: 'functionStart', value: '${', start: i, end: i + 2 });
      i += 2;
      continue;
    }
    if (input.startsWith('$simple{', i)) {
      tokens.push({ type: 'functionStart', value: '$simple{', start: i, end: i + 8 });
      i += 8;
      continue;
    }

    // Function end
    if (input[i] === '}') {
      tokens.push({ type: 'functionEnd', value: '}', start: i, end: i + 1 });
      i += 1;
      continue;
    }

    // Escape sequences
    if (input[i] === '\\' && i + 1 < len) {
      const next = input[i + 1];
      tokens.push({ type: 'escape', value: '\\' + next, start: i, end: i + 2 });
      i += 2;
      continue;
    }

    // Single quote
    if (input[i] === "'") {
      tokens.push({ type: 'singleQuote', value: "'", start: i, end: i + 1 });
      i += 1;
      continue;
    }

    // Double quote
    if (input[i] === '"') {
      tokens.push({ type: 'doubleQuote', value: '"', start: i, end: i + 1 });
      i += 1;
      continue;
    }

    // Whitespace
    if (input[i] === ' ' || input[i] === '\t' || input[i] === '\n' || input[i] === '\r') {
      let start = i;
      while (i < len && (input[i] === ' ' || input[i] === '\t' || input[i] === '\n' || input[i] === '\r')) {
        i++;
      }
      tokens.push({ type: 'whitespace', value: input.substring(start, i), start, end: i });
      continue;
    }

    // Text (everything else — accumulated into runs)
    let start = i;
    while (i < len && input[i] !== '$' && input[i] !== '}' && input[i] !== '\\' &&
           input[i] !== "'" && input[i] !== '"' &&
           input[i] !== ' ' && input[i] !== '\t' && input[i] !== '\n' && input[i] !== '\r') {
      i++;
    }
    if (i > start) {
      tokens.push({ type: 'text', value: input.substring(start, i), start, end: i });
    }
  }

  return tokens;
}

// ========================================================================
// Validator
// ========================================================================

function findClosestFunction(name) {
  const names = Object.keys(FUNCTIONS);
  let best = null;
  let bestDist = Infinity;
  for (const fn of names) {
    const d = levenshtein(name.toLowerCase(), fn.toLowerCase());
    if (d < bestDist && d <= 3) {
      bestDist = d;
      best = fn;
    }
  }
  return best;
}

function levenshtein(a, b) {
  const m = a.length, n = b.length;
  if (m === 0) return n;
  if (n === 0) return m;
  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 0; i <= m; i++) dp[i][0] = i;
  for (let j = 0; j <= n; j++) dp[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i-1] === b[j-1]
        ? dp[i-1][j-1]
        : 1 + Math.min(dp[i-1][j], dp[i][j-1], dp[i-1][j-1]);
    }
  }
  return dp[m][n];
}

function extractBaseName(content) {
  let base = content;
  for (const ch of ['(', '.', ':']) {
    const pos = base.indexOf(ch);
    if (pos !== -1) {
      base = base.substring(0, pos);
    }
  }
  return base;
}

function isKnownOperator(word) {
  return word in OPERATORS;
}

function findBinaryOperator(text) {
  const binaryOps = Object.entries(OPERATORS)
    .filter(([, op]) => op.kind === 'binary')
    .map(([sym]) => sym)
    .sort((a, b) => b.length - a.length);
  for (const op of binaryOps) {
    if (text === op) return op;
  }
  return null;
}

function validate(input, mode) {
  if (mode === undefined) mode = 'expression';
  const diagnostics = [];
  const tokens = tokenize(input);

  // Track delimiter balancing
  let functionDepth = 0;
  const functionStarts = [];
  let inSingleQuote = false;
  let singleQuoteStart = -1;
  let inDoubleQuote = false;
  let doubleQuoteStart = -1;

  // Collect function contents for validation
  const functionBlocks = [];
  let currentFunctionStart = -1;
  let currentFunctionTokenStart = -1;

  for (let t = 0; t < tokens.length; t++) {
    const tok = tokens[t];

    if (tok.type === 'singleQuote' && !inDoubleQuote) {
      if (inSingleQuote) {
        inSingleQuote = false;
      } else {
        inSingleQuote = true;
        singleQuoteStart = tok.start;
      }
      continue;
    }

    if (tok.type === 'doubleQuote' && !inSingleQuote) {
      if (inDoubleQuote) {
        inDoubleQuote = false;
      } else {
        inDoubleQuote = true;
        doubleQuoteStart = tok.start;
      }
      continue;
    }

    // Inside quotes, only check for unmatched function braces
    if (inSingleQuote || inDoubleQuote) {
      if (tok.type === 'functionStart') {
        functionDepth++;
        functionStarts.push(tok.start);
        currentFunctionStart = tok.start;
        currentFunctionTokenStart = t;
      } else if (tok.type === 'functionEnd') {
        if (functionDepth > 0) {
          functionDepth--;
          const fnStart = functionStarts.pop();
          const fnContent = input.substring(fnStart + 2, tok.start);
          functionBlocks.push({ content: fnContent, start: fnStart, end: tok.end });
        }
      }
      continue;
    }

    if (tok.type === 'functionStart') {
      functionDepth++;
      functionStarts.push(tok.start);
      currentFunctionStart = tok.start;
      currentFunctionTokenStart = t;
    } else if (tok.type === 'functionEnd') {
      if (functionDepth > 0) {
        functionDepth--;
        const fnStart = functionStarts.pop();
        const fnContent = input.substring(fnStart + (input.startsWith('$simple{', fnStart) ? 8 : 2), tok.start);
        functionBlocks.push({ content: fnContent, start: fnStart, end: tok.end });
      } else {
        diagnostics.push({
          severity: 'error',
          message: "Unexpected '}' without matching '${' ",
          start: tok.start,
          end: tok.end
        });
      }
    }

    // Validate escape sequences
    if (tok.type === 'escape') {
      const escaped = tok.value[1];
      if (!['n', 't', 'r', '}', '\\', "'", '"'].includes(escaped)) {
        diagnostics.push({
          severity: 'warning',
          message: `Unusual escape sequence '\\${escaped}'`,
          start: tok.start,
          end: tok.end
        });
      }
    }
  }

  // Check for unclosed delimiters
  if (functionDepth > 0) {
    const unclosedStart = functionStarts[functionStarts.length - 1];
    diagnostics.push({
      severity: 'error',
      message: "'${' has no closing '}'",
      start: unclosedStart,
      end: unclosedStart + 2
    });
  }

  if (inSingleQuote) {
    diagnostics.push({
      severity: 'error',
      message: "Single quote has no closing quote",
      start: singleQuoteStart,
      end: singleQuoteStart + 1
    });
  }

  if (inDoubleQuote) {
    diagnostics.push({
      severity: 'error',
      message: "Double quote has no closing quote",
      start: doubleQuoteStart,
      end: doubleQuoteStart + 1
    });
  }

  // Validate function names
  for (const block of functionBlocks) {
    validateFunction(block.content, block.start, block.end, diagnostics);
  }

  // Validate predicate structure
  if (mode === 'predicate') {
    validatePredicate(input, tokens, diagnostics);
  }

  return {
    valid: diagnostics.filter(d => d.severity === 'error').length === 0,
    diagnostics
  };
}

function validateFunction(content, blockStart, blockEnd, diagnostics) {
  // Handle nested functions — only validate the outermost name
  const baseName = extractBaseName(content);

  if (!baseName || baseName.length === 0) {
    diagnostics.push({
      severity: 'error',
      message: "Empty function reference '${}' ",
      start: blockStart,
      end: blockEnd
    });
    return;
  }

  // Skip validation for contents that start with ${ (nested function as argument)
  if (baseName.startsWith('$')) {
    return;
  }

  // Check if function name is known
  if (!(baseName in FUNCTIONS)) {
    const suggestion = findClosestFunction(baseName);
    const msg = suggestion
      ? `Unknown function '${baseName}', did you mean '${suggestion}'?`
      : `Unknown function '${baseName}'`;
    diagnostics.push({
      severity: 'error',
      message: msg,
      start: blockStart,
      end: blockEnd,
      suggestion
    });
  }
}

function validatePredicate(input, tokens, diagnostics) {
  // Build a simplified structure: segments separated by operators
  // We look for operator tokens in the non-quoted, non-function text
  const segments = [];
  let current = '';
  let currentStart = 0;
  let depth = 0;
  let inQuote = false;
  let quoteChar = null;

  for (let i = 0; i < input.length; i++) {
    const ch = input[i];

    // Track quote state
    if ((ch === "'" || ch === '"') && depth === 0) {
      if (!inQuote) {
        inQuote = true;
        quoteChar = ch;
      } else if (ch === quoteChar) {
        inQuote = false;
        quoteChar = null;
      }
      current += ch;
      continue;
    }

    if (inQuote) {
      current += ch;
      continue;
    }

    // Track function depth
    if (ch === '$' && i + 1 < input.length && input[i + 1] === '{') {
      depth++;
      current += '${';
      i++;
      continue;
    }
    if (ch === '}') {
      if (depth > 0) depth--;
      current += '}';
      continue;
    }

    // Outside functions and quotes — check for operators
    if (depth === 0) {
      let rest = input.substring(i).trimStart();
      let skipWs = input.substring(i).length - rest.length;
      let opMatch = null;

      // Check all binary/logical operators (longest match first)
      const allOps = Object.entries(OPERATORS)
        .filter(([, op]) => op.kind === 'binary' || op.kind === 'logical')
        .map(([sym]) => sym)
        .sort((a, b) => b.length - a.length);

      for (const op of allOps) {
        if (rest.startsWith(op) && (rest.length === op.length || rest[op.length] === ' ')) {
          opMatch = op;
          break;
        }
      }

      if (opMatch && (ch === ' ' || current.trim().length === 0)) {
        // Found an operator — save the left segment
        if (current.trim().length > 0) {
          segments.push({ type: 'operand', value: current.trim(), start: currentStart });
        }
        const opStart = i + skipWs;
        segments.push({ type: 'operator', value: opMatch, start: opStart, kind: OPERATORS[opMatch].kind });
        i = opStart + opMatch.length;
        current = '';
        currentStart = i;
        continue;
      }
    }

    current += ch;
  }

  if (current.trim().length > 0) {
    segments.push({ type: 'operand', value: current.trim(), start: currentStart });
  }

  // Check for misspelled word operators between operands
  const wordOperators = Object.keys(OPERATORS).filter(op => /^[a-zA-Z!]/.test(op));
  for (let s = 0; s < segments.length; s++) {
    const seg = segments[s];
    if (seg.type === 'operand') {
      const words = seg.value.split(/\s+/);
      for (const word of words) {
        if (word.length >= 2 && /^!?[a-zA-Z]+$/.test(word) && !findBinaryOperator(word)) {
          for (const op of wordOperators) {
            if (levenshtein(word.toLowerCase(), op.toLowerCase()) <= 2 && word.toLowerCase() !== op.toLowerCase()) {
              diagnostics.push({
                severity: 'warning',
                message: `'${word}' looks like a misspelled operator, did you mean '${op}'?`,
                start: seg.start + seg.value.indexOf(word),
                end: seg.start + seg.value.indexOf(word) + word.length,
                suggestion: op
              });
              break;
            }
          }
        }
      }
    }
  }

  // Validate operator usage
  for (let s = 0; s < segments.length; s++) {
    const seg = segments[s];
    if (seg.type === 'operator') {
      const opInfo = OPERATORS[seg.value];
      if (opInfo && (opInfo.kind === 'binary' || opInfo.kind === 'logical')) {
        // Check for LHS
        const prev = s > 0 ? segments[s - 1] : null;
        if (!prev || prev.type !== 'operand') {
          diagnostics.push({
            severity: 'error',
            message: `Operator '${seg.value}' has no left-hand side operand`,
            start: seg.start,
            end: seg.start + seg.value.length
          });
        }
        // Check for RHS
        const next = s + 1 < segments.length ? segments[s + 1] : null;
        if (!next || next.type !== 'operand') {
          diagnostics.push({
            severity: 'error',
            message: `Operator '${seg.value}' has no right-hand side operand`,
            start: seg.start,
            end: seg.start + seg.value.length
          });
        }
      }
    }
  }
}

// ========================================================================
// Autocomplete
// ========================================================================

function complete(input, cursor) {
  const suggestions = [];

  // Find context at cursor position
  const before = input.substring(0, cursor);
  const lastDollarBrace = before.lastIndexOf('${');
  const lastCloseBrace = before.lastIndexOf('}');

  if (lastDollarBrace > lastCloseBrace) {
    // We are inside a ${...} — suggest function names
    const partial = before.substring(lastDollarBrace + 2);
    const basePart = extractBaseName(partial).toLowerCase();

    for (const [name, fns] of Object.entries(FUNCTIONS)) {
      if (name.toLowerCase().startsWith(basePart)) {
        for (const fn of fns) {
          suggestions.push({
            label: fn.name,
            displayName: fn.displayName,
            description: fn.description,
            group: fn.group,
            insertText: fn.name
          });
        }
      }
    }
  } else if (before.trimEnd().endsWith('}') || /\S$/.test(before)) {
    // After a function or operand — suggest operators
    for (const [sym, op] of Object.entries(OPERATORS)) {
      if (op.kind === 'binary' || op.kind === 'logical') {
        suggestions.push({
          label: sym,
          displayName: op.displayName,
          description: op.description,
          insertText: ' ' + sym + ' '
        });
      }
    }
  }

  return suggestions;
}

// ========================================================================
// Public API
// ========================================================================

function getFunctions() {
  return FUNCTIONS;
}

function getOperators() {
  return OPERATORS;
}

// ========================================================================
// Exports (works in both Node.js and browser)
// ========================================================================

const CamelSimpleValidator = { validate, complete, getFunctions, getOperators, tokenize };

if (typeof module !== 'undefined' && module.exports) {
  module.exports = CamelSimpleValidator;
}
if (typeof globalThis !== 'undefined') {
  globalThis.CamelSimpleValidator = CamelSimpleValidator;
}

// ========================================================================
// Self-test (run with: node camel-simple-validator.js)
// ========================================================================

if (typeof require !== 'undefined' && require.main === module) {
  let passed = 0;
  let failed = 0;

  function test(name, input, mode, expectValid, expectErrorSubstring) {
    const result = validate(input, mode);
    let ok = result.valid === expectValid;
    if (expectErrorSubstring) {
      const hasMatch = result.diagnostics.some(d => d.message.includes(expectErrorSubstring));
      ok = ok && hasMatch;
    }
    if (ok) {
      passed++;
    } else {
      failed++;
      console.log(`FAIL: ${name}`);
      console.log(`  input: ${JSON.stringify(input)}`);
      console.log(`  expected valid=${expectValid}` + (expectErrorSubstring ? `, error containing "${expectErrorSubstring}"` : ''));
      console.log(`  got valid=${result.valid}, diagnostics:`, JSON.stringify(result.diagnostics, null, 2));
    }
  }

  // === Valid expressions ===
  test('simple body', '${body}', 'expression', true);
  test('header access', '${header.foo}', 'expression', true);
  test('template text', 'Hello ${body}', 'expression', true);
  test('multiple functions', '${header.from} to ${header.to}', 'expression', true);
  test('nested function in text', "Hello ${header.name}, you have ${header.count} items", 'expression', true);
  test('exchangeId', '${exchangeId}', 'expression', true);
  test('function with parens', '${trim()}', 'expression', true);
  test('function with args', '${substring(1,3)}', 'expression', true);
  test('plain text', 'Hello World', 'expression', true);
  test('empty string', '', 'expression', true);
  test('env variable', '${env.HOME}', 'expression', true);
  test('sys property', '${sys.user.name}', 'expression', true);
  test('properties', '${properties:myKey}', 'expression', true);
  test('date function', '${date(now)}', 'expression', true);
  test('camelId', '${camelId}', 'expression', true);
  test('routeId', '${routeId}', 'expression', true);
  test('exchangeProperty', '${exchangeProperty.myProp}', 'expression', true);
  test('variable', '${variable.myVar}', 'expression', true);
  test('bodyAs', '${bodyAs(String)}', 'expression', true);
  test('uppercase', '${uppercase(${body})}', 'expression', true);
  test('escape newline', 'Hello\\nWorld', 'expression', true);
  test('escape tab', 'col1\\tcol2', 'expression', true);
  test('$simple prefix', '$simple{body}', 'expression', true);

  // === Valid predicates ===
  test('simple equality', "${body} == 'foo'", 'predicate', true);
  test('numeric comparison', '${header.count} > 5', 'predicate', true);
  test('contains', "${header.title} contains 'Camel'", 'predicate', true);
  test('logical AND', "${body} == 'foo' && ${header.bar} == 'baz'", 'predicate', true);
  test('logical OR', "${body} == 'foo' || ${body} == 'bar'", 'predicate', true);
  test('not equal', "${header.type} != 'test'", 'predicate', true);
  test('regex operator', "${header.code} regex '\\d{3}'", 'predicate', true);
  test('in operator', "${header.color} in 'red,blue,green'", 'predicate', true);
  test('range operator', "${header.age} range '18..65'", 'predicate', true);
  test('is operator', "${body} is 'String'", 'predicate', true);
  test('startsWith', "${header.name} startsWith 'Camel'", 'predicate', true);
  test('endsWith', "${header.file} endsWith '.xml'", 'predicate', true);
  test('case-insensitive contains', "${header.title} ~~ 'camel'", 'predicate', true);
  test('greater or equal', '${header.count} >= 10', 'predicate', true);
  test('less or equal', '${header.count} <= 100', 'predicate', true);

  // === Invalid expressions ===
  test('unclosed function', '${body', 'expression', false, "no closing '}'");
  test('extra closing brace', 'body}', 'expression', false, "Unexpected '}'");
  test('unknown function', '${boddy}', 'expression', false, "Unknown function 'boddy'");
  test('unknown function with suggestion', '${headr.foo}', 'expression', false, "did you mean 'header'");
  test('empty function', '${}', 'expression', false, "Empty function");
  test('unclosed single quote', "Hello '${body}", 'expression', false, "Single quote has no closing");
  test('unclosed double quote', 'Hello "${body}', 'expression', false, "Double quote has no closing");
  test('unknown function trim typo', '${trm()}', 'expression', false, "did you mean 'trim'");

  // === Invalid predicates ===
  test('operator no RHS', "${body} ==", 'predicate', false, "no right-hand side");
  test('operator no LHS', "== 'foo'", 'predicate', false, "no left-hand side");

  // === Predicate warnings ===
  test('misspelled operator', "${header.foo} contans 'bar'", 'predicate', true, "did you mean 'contains'");

  // === Summary ===
  console.log(`\n${passed + failed} tests: ${passed} passed, ${failed} failed`);
  if (failed > 0) {
    process.exit(1);
  }
}
