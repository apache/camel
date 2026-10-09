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
// DataWeave semantics for DataSonnet, used by the DataWeave to DataSonnet conversion (camel-dataweave).
// Import with: local dw = import 'camel-dataweave.libsonnet';
//
// In DataWeave a missing field, an index out of range and a selector on null give null, a field selector on an
// array selects the field of every element, and most functions accept null. In Jsonnet these are errors.
// Functions that are given a function call it with all the arguments DataWeave passes (such as item and index).
{
  local dw = self,
  local indexes(a) = std.range(0, std.length(a) - 1),

  // -- Selectors

  // An XML element with only text (and attributes) is read as an object with the text in '$'
  local isText(x) = std.isObject(x) && std.objectHas(x, '$')
                    && std.all([k == '$' || k == '~' || std.startsWith(k, '@') for k in std.objectFields(x)]),
  local text(x) = if isText(x) then x['$'] else x,

  // An XML element is read as an object with its position in '~'
  local isElement(x) = std.isObject(x) && std.objectHas(x, '~'),

  // x.k: null when missing; on an array the field of every element that has it; of an XML element repeated under
  // the same name (read as an array) the first
  selRaw(x, k)::
    if std.isObject(x) then (
      if !std.objectHas(x, k) then null
      else
        local v = x[k];
        if std.isArray(v) && std.length(v) > 0 && std.all(std.map(isElement, v)) then v[0] else v
    )
    else if std.isArray(x) then [e[k] for e in x if std.isObject(e) && std.objectHas(e, k)]
    else null,
  sel(x, k)::
    local v = dw.selRaw(x, k);
    if std.isArray(x) then std.map(text, v) else text(v),
  // x.a.b.c
  pathRaw(x, ks):: std.foldl(function(v, k) dw.selRaw(v, k), ks, x),
  path(x, ks):: std.foldl(function(v, k) dw.sel(v, k), ks, x),
  // x.@a
  attr(x, a):: dw.selRaw(x, '@' + a),
  // x.*k: all the values of k (an XML element repeated under the same name is read as an array)
  multiRaw(x, k)::
    if std.isObject(x) then (if !std.objectHas(x, k) then null else if std.isArray(x[k]) then x[k] else [x[k]])
    else if std.isArray(x) then std.flattenArrays([dw.multiRaw(e, k) for e in x if dw.multiRaw(e, k) != null])
    else null,
  multi(x, k):: local v = dw.multiRaw(x, k); if v == null then null else std.map(text, v),
  // x..k: the values of k at any depth
  desc(x, k)::
    local walk(v) =
      if std.isObject(v) then
        (if std.objectHas(v, k) then [text(v[k])] else []) + std.flattenArrays([walk(v[f]) for f in std.objectFields(v)])
      else if std.isArray(v) then std.flattenArrays([walk(e) for e in v])
      else [];
    if x == null then null else walk(x),
  // x[i]: from the end when negative, null when out of range; x["k"] is x.k
  idx(x, i)::
    if x == null || i == null then null
    else if std.isString(i) then dw.sel(x, i)
    else if std.isArray(x) || std.isString(x) then (
      local n = std.length(x);
      local j = if i < 0 then n + i else i;
      if j < 0 || j >= n then null else x[j]
    )
    else if std.isObject(x) then (
      local ks = std.objectFields(x);
      local j = if i < 0 then std.length(ks) + i else i;
      if j < 0 || j >= std.length(ks) then null else x[ks[j]]
    )
    else null,
  // x[a to b]: inclusive, from the end when negative, reversed when a > b
  slice(x, a, b)::
    if x == null then null
    else
      local n = std.length(x);
      local from = if a < 0 then n + a else a;
      local to = if b < 0 then n + b else b;
      local lo = std.max(0, std.min(from, to));
      local hi = std.min(n - 1, std.max(from, to));
      local part = if std.isString(x) then std.substr(x, lo, hi - lo + 1) else x[lo:hi + 1];
      if from <= to then part
      else if std.isString(x) then std.join('', std.reverse(std.stringChars(part)))
      else std.reverse(part),
  // a to b
  range(a, b):: if a <= b then std.range(a, b) else std.reverse(std.range(b, a)),
  // x.k?
  has(x, k):: std.isObject(x) && std.objectHas(x, k),

  // -- Values

  default(v, fallback):: if v == null then fallback else v,
  // a value in a string (interpolation, dynamic key)
  str(v):: if std.isString(v) then v else std.toString(v),
  toString(v):: if v == null || std.isString(v) then v else std.toString(v),
  nullSafe(f, x):: if x == null then null else f(x),
  // a function given as a value, called with up to three arguments
  fn(f)::
    local n = std.length(f);
    if n == 0 then function(a=null, b=null, c=null) f()
    else if n == 1 then function(a, b=null, c=null) f(a)
    else if n == 2 then function(a, b=null, c=null) f(a, b)
    else f,
  typeOf(x)::
    if x == null then 'Null'
    else if std.isString(x) then 'String'
    else if std.isNumber(x) then 'Number'
    else if std.isBoolean(x) then 'Boolean'
    else if std.isArray(x) then 'Array'
    else if std.isObject(x) then 'Object'
    else 'Function',
  // a ~= b
  similar(a, b):: a == b || (a != null && b != null && dw.str(a) == dw.str(b)),
  // an object spread { (x) }: an object, or an array of objects
  toObject(x)::
    if x == null then {}
    else if std.isArray(x) then std.foldl(function(acc, o) acc + dw.toObject(o), x, {})
    else x,
  // obj - "key", array - element
  minus(a, b)::
    if std.isObject(a) then std.foldl(function(acc, k) if k == b then acc else acc { [k]: a[k] }, std.objectFields(a), {})
    else if std.isArray(a) then [e for e in a if e != b]
    else a - b,
  // obj -- ["k1", "k2"] (or an object with the keys), array -- elements
  removeAll(a, b)::
    local keys = if std.isObject(b) then std.objectFields(b) else b;
    if a == null then null
    else if std.isObject(a) then std.foldl(function(acc, k) if std.member(keys, k) then acc else acc { [k]: a[k] },
                                           std.objectFields(a), {})
    else [e for e in a if !std.member(keys, e)],
  // the skipNullOn writer property: everywhere, objects or arrays
  skipNulls(v, where)::
    local objects = where == 'everywhere' || where == 'objects';
    local arrays = where == 'everywhere' || where == 'arrays';
    if std.isObject(v) then
      std.foldl(function(acc, k) if objects && v[k] == null then acc else acc { [k]: dw.skipNulls(v[k], where) },
                std.objectFields(v), {})
    else if std.isArray(v) then [dw.skipNulls(e, where) for e in v if !(arrays && e == null)]
    else v,

  // -- Strings

  upper(s):: if s == null then null else std.asciiUpper(s),
  lower(s):: if s == null then null else std.asciiLower(s),
  trim(s):: if s == null then null else std.stripChars(s, ' \t\n\r'),
  isBlank(s):: s == null || std.length(std.stripChars(s, ' \t\n\r')) == 0,
  contains(a, b)::
    if a == null then false
    else if std.isArray(a) then std.member(a, b)
    else std.length(std.findSubstr(b, a)) > 0,
  // contains /regex/, with scan the ds.scan function
  containsMatch(s, scan, regex):: s != null && std.length(scan(s, regex)) > 0,
  // splitBy /regex/, with split the ds.splitBy function
  splitByMatch(s, split, regex):: if s == null then null else split(s, regex),
  startsWith(s, prefix):: s != null && std.startsWith(s, prefix),
  endsWith(s, suffix):: s != null && std.endsWith(s, suffix),
  splitBy(s, separator)::
    if s == null then null
    else if separator == '' then std.stringChars(s)
    else std.split(s, separator),
  joinBy(a, separator):: if a == null then null else std.join(separator, [dw.str(e) for e in a]),
  replace(s, target, replacement):: if s == null then null else std.strReplace(s, target, replacement),
  // replace /regex/ with a function of the match (the matched text and its groups), with starts and matches as given
  // by ds.find and ds.scan
  replaceMatches(s, starts, matches, f)::
    if s == null then null
    else
      local r = std.foldl(function(acc, i) {
        out: acc.out + std.substr(s, acc.pos, starts[i] - acc.pos) + dw.str(f(matches[i])),
        pos: starts[i] + std.length(matches[i][0]),
      }, indexes(matches), { out: '', pos: 0 });
      r.out + std.substr(s, r.pos, std.length(s) - r.pos),

  // -- Arrays and objects

  sizeOf(x):: if x == null then null else std.length(x),
  isEmpty(x):: x == null || std.length(x) == 0,
  map(a, f):: if a == null then null else std.mapWithIndex(function(i, x) f(x, i), a),
  filter(a, f)::
    if a == null then null
    else if std.isString(a) then (
      local cs = std.stringChars(a);
      std.join('', [cs[i] for i in indexes(cs) if f(cs[i], i)])
    )
    else if std.isObject(a) then dw.filterObject(a, function(v, k, i) f(v, k))
    else [a[i] for i in indexes(a) if f(a[i], i)],
  flatMap(a, f):: if a == null then null else std.flattenArrays(dw.map(a, f)),
  // reduce with an initial accumulator, and without (starting with the first item)
  reduce(a, f, init):: if a == null then null else std.foldl(function(acc, x) f(x, acc), a, init),
  reduce1(a, f)::
    if a == null || std.length(a) == 0 then null
    else std.foldl(function(acc, x) f(x, acc), a[1:], a[0]),
  distinctBy(a, f)::
    if a == null then null
    else if std.isObject(a) then
      std.foldl(function(acc, k)
                  local g = f(a[k], k);
                  if std.member(acc.keys, g) then acc else { keys: acc.keys + [g], obj: acc.obj { [k]: a[k] } },
                std.objectFields(a), { keys: [], obj: {} }).obj
    else
      std.foldl(function(acc, i)
                  local g = f(a[i], i);
                  if std.member(acc.keys, g) then acc else { keys: acc.keys + [g], items: acc.items + [a[i]] },
                indexes(a), { keys: [], items: [] }).items,
  groupBy(a, f)::
    if a == null then null
    else if std.isObject(a) then
      std.foldl(function(acc, k)
                  local g = dw.str(f(a[k], k));
                  acc { [g]: (if std.objectHas(acc, g) then acc[g] else {}) + { [k]: a[k] } },
                std.objectFields(a), {})
    else
      std.foldl(function(acc, i)
                  local g = dw.str(f(a[i], i));
                  acc { [g]: (if std.objectHas(acc, g) then acc[g] else []) + [a[i]] },
                indexes(a), {}),
  // a stable sort by key (std.sort of DataSonnet ignores keyF for an array of numbers or strings)
  local sortBy(xs, key) =
    local keyed = [{ k: key(x), x: x } for x in xs];
    std.flattenArrays([[e.x for e in keyed if e.k == k] for k in std.set([e.k for e in keyed])]),
  orderBy(a, f)::
    if a == null then null
    else if std.isObject(a) then
      dw.toObject(sortBy([{ [k]: a[k] } for k in std.objectFields(a)],
                         function(e) local k = std.objectFields(e)[0]; f(e[k], k)))
    else std.map(function(e) e.x, sortBy([{ i: i, x: a[i] } for i in indexes(a)], function(e) f(e.x, e.i))),
  mapObject(o, f)::
    if o == null then null
    else
      local ks = std.objectFields(o);
      std.foldl(function(acc, i) acc + f(o[ks[i]], ks[i], i), indexes(ks), {}),
  filterObject(o, f)::
    if o == null then null
    else
      local ks = std.objectFields(o);
      std.foldl(function(acc, i) if f(o[ks[i]], ks[i], i) then acc { [ks[i]]: o[ks[i]] } else acc, indexes(ks), {}),
  pluck(o, f)::
    if o == null then null
    else
      local ks = std.objectFields(o);
      [f(o[ks[i]], ks[i], i) for i in indexes(ks)],
  keysOf(o):: if o == null then null else std.objectFields(o),
  namesOf(o):: dw.keysOf(o),
  valuesOf(o):: if o == null then null else [o[k] for k in std.objectFields(o)],
  entriesOf(o):: if o == null then null else [{ key: k, value: o[k], attributes: {} } for k in std.objectFields(o)],
  flatten(a):: if a == null then null else std.flattenArrays([if std.isArray(e) then e else [e] for e in a]),
  zip(a, b)::
    if a == null || b == null then null
    else [[a[i], b[i]] for i in std.range(0, std.min(std.length(a), std.length(b)) - 1)],
  // x then f
  pipe(v, f):: f(v),
  sum(a):: if a == null then null else std.foldl(function(acc, x) acc + x, a, 0),
  avg(a):: if a == null then null else dw.sum(a) / std.length(a),
  min(a):: if a == null || std.length(a) == 0 then null else std.foldl(function(m, x) if x < m then x else m, a[1:], a[0]),
  max(a):: if a == null || std.length(a) == 0 then null else std.foldl(function(m, x) if x > m then x else m, a[1:], a[0]),
  maxBy(a, f)::
    if a == null || std.length(a) == 0 then null
    else std.foldl(function(m, x) if f(x) > f(m) then x else m, a[1:], a[0]),
  minBy(a, f)::
    if a == null || std.length(a) == 0 then null
    else std.foldl(function(m, x) if f(x) < f(m) then x else m, a[1:], a[0]),
  sumBy(a, f):: if a == null then null else std.foldl(function(acc, x) acc + f(x), a, 0),
  countBy(a, f):: if a == null then null else std.length([x for x in a if f(x)]),
  every(a, f):: a == null || std.all([f(x) for x in a]),
  some(a, f):: a != null && std.any([f(x) for x in a]),
  firstWith(a, f):: if a == null then null else (local m = [x for x in a if f(x)]; if std.length(m) == 0 then null else m[0]),
  partition(a, f):: if a == null then null else { success: [x for x in a if f(x)], failure: [x for x in a if !f(x)] },
  indexOf(a, x)::
    if a == null then -1
    else if std.isString(a) then (local found = std.findSubstr(x, a); if std.length(found) == 0 then -1 else found[0])
    else (local found = [i for i in indexes(a) if a[i] == x]; if std.length(found) == 0 then -1 else found[0]),
  lastIndexOf(a, x)::
    if a == null then -1
    else if std.isString(a) then (local found = std.findSubstr(x, a); if std.length(found) == 0 then -1 else found[std.length(found) - 1])
    else (local found = [i for i in indexes(a) if a[i] == x]; if std.length(found) == 0 then -1 else found[std.length(found) - 1]),
  take(a, n):: if a == null then null else a[0:std.max(0, std.min(n, std.length(a)))],
  drop(a, n):: if a == null then null else a[std.max(0, std.min(n, std.length(a))):],
  splitAt(a, n):: if a == null then null else { l: dw.take(a, n), r: dw.drop(a, n) },
  divideBy(a, n):: if a == null then null else [a[i:std.min(i + n, std.length(a))] for i in std.range(0, std.length(a) - 1) if i % n == 0],

  // -- Numbers

  abs(x):: if x == null then null else if x < 0 then -x else x,
  // half up, as DataWeave
  round(x):: if x == null then null else if x < 0 then -std.floor(-x + 0.5) else std.floor(x + 0.5),
  isEven(x):: x % 2 == 0,
  isOdd(x):: x % 2 != 0,
  isInteger(x):: std.isNumber(x) && std.floor(x) == x,
  isDecimal(x):: std.isNumber(x) && std.floor(x) != x,
}
