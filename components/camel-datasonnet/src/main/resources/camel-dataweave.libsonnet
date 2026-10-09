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
//
// DataSonnet evaluates the argument of a function each time the function uses it, and an element of an array each
// time it is used: only the value of an object field is kept. A function that uses an argument more than once starts
// with strict, else nested calls are evaluated over and over (exponentially in the depth of the nesting).
{
  local dw = self,
  local indexes(a) = std.range(0, std.length(a) - 1),
  // f(x), with x evaluated once (the initial value of std.foldl is evaluated once, and kept)
  local strict(x, f) = std.foldl(function(v, _) f(v), [0], x),

  // -- XML
  //
  // The DataSonnet XML reader gives an element as an object with its position in '~', its attributes in '@name' keys,
  // its namespace declarations in '@xmlns', its text in '$' (mixed content in '$1', '$2', ..., CDATA also in '#1',
  // ...), and its child elements by name (with the prefix of their namespace, such as 'soapenv:Body'); an element
  // repeated under the same name is an array. In DataWeave the value of an element without child elements is its text
  // (null when empty), a selector matches the local name of an element whatever its namespace, and the attributes are
  // metadata that only the .@ selector and an XML output see.

  local isElement(x) = std.isObject(x) && std.objectHas(x, '~'),
  // the reader gives an array only for a repeated element, so the first element tells
  local isRepeated(v) = std.isArray(v) && std.length(v) > 0 && isElement(v[0]),
  // an element, or a document (the object with the root element)
  local isXml(x) =
    isElement(x) || std.isObject(x) && (local fs = std.objectFields(x);
                                        std.length(fs) == 1 && (isElement(x[fs[0]]) || isRepeated(x[fs[0]]))),
  local isMeta(k) = k == '~' || std.startsWith(k, '@'),
  local isTextKey(k) = std.startsWith(k, '$') || std.startsWith(k, '#'),
  local isText(x) = isElement(x) && std.all([isMeta(k) || isTextKey(k) for k in std.objectFields(x)]),
  local textOf(x) = strict(std.join('', [x[k] for k in std.objectFields(x) if std.startsWith(k, '$')]),
                           function(t) if t == '' then null else t),
  // (an element with child elements is marked as selected: written as XML under another key, its attributes are left
  // out, as they are those of its key in the input)
  local text(x0) = strict(x0, function(x)
    if isText(x) then textOf(x) else if isElement(x) then x + { '~selected':: true } else x),
  local localName(k) = local i = std.findSubstr(':', k); if std.length(i) == 0 then k else k[i[std.length(i) - 1] + 1:],
  // the key of the child k of an XML element: k, or else a child with the local name k
  local childKey(x, k) =
    if std.objectHas(x, k) then k
    else if !isXml(x) then null
    else strict([f for f in std.objectFields(x) if !isMeta(f) && !isTextKey(f) && localName(f) == k],
                function(m) if std.length(m) == 0 then null else m[0]),
  local first(v) = if isRepeated(v) then v[0] else v,
  // a child element with the namespace declarations in scope of its parent, so its namespace can be resolved (and it
  // can be written as XML) without its ancestors
  local inScope(x, v0) = strict(v0, function(v)
    if !std.isObject(x) || !std.objectHas(x, '@xmlns') then v
    else if isElement(v) then v { '@xmlns': x['@xmlns'] + (if std.objectHas(v, '@xmlns') then v['@xmlns'] else {}) }
    else if isRepeated(v) then [inScope(x, e) for e in v]
    else v),
  // the position of a child in its element: the order of child elements, or the number of a text key ($2)
  local position(k, v) =
    if isElement(v) then v['~']
    else if std.length(k) > 1 && isTextKey(k) then std.parseInt(k[1:])
    else 0,
  local attrsOf(x0) = strict(x0, function(x)
    if !isElement(x) then {}
    else { [k[1:]]: x[k] for k in std.objectFields(x) if std.startsWith(k, '@') && k != '@xmlns' }),

  // The value of an XML element as DataWeave sees it: its text, or its child elements
  text(x):: text(x),
  // The entries of an object as DataWeave sees them: for an XML element without its attributes and order, with the
  // local names, an entry for each element repeated under a name, and the text of the elements without children
  local entry(k, e) = { k: localName(k), v: text(e), a: attrsOf(e), p: position(k, e) },
  local entries(o0) = strict(o0, function(o)
    if !isXml(o) then [{ k: k, v: o[k], a: {} } for k in std.objectFields(o)]
    else
      local keys = [k for k in std.objectFields(o) if !isMeta(k) && !std.startsWith(k, '#')];
      local es = std.flattenArrays([
        if isTextKey(k) then [{ k: '__text', v: o[k], a: {}, p: position(k, o[k]) }]
        else strict(inScope(o, o[k]), function(v) if isRepeated(v) then [entry(k, e) for e in v] else [entry(k, v)])
        for k in keys
      ]);
      // in document order, which the keys are unless an element repeats with others in between, or text is mixed in
      if std.length(keys) > 1 && std.any([std.isArray(o[k]) || isTextKey(k) for k in keys])
      then std.sort(es, function(e) e.p)
      else es),
  // An object of entries; the values of a key that repeats become an array (an object can not repeat a key)
  local fromEntries(es0) = strict(es0, function(es)
    local keys = std.foldl(function(acc, e) if std.member(acc, e.k) then acc else acc + [e.k], es, []);
    { [k]: strict([e.v for e in es if e.k == k], function(vs) if std.length(vs) == 1 then vs[0] else vs) for k in keys }),
  entries(o):: entries(o),
  // The DataWeave value of something read from XML, for writing it in another format than XML: elements as their text
  // or as objects of their child elements, without attributes, order and namespace prefixes
  fromXml(x0):: strict(x0, function(x)
    if isText(x) then textOf(x)
    else if std.isArray(x) then std.map(dw.fromXml, x)
    else if isXml(x) then fromEntries([{ k: e.k, v: dw.fromXml(e.v) } for e in entries(x)])
    else if std.isObject(x) then { [k]: dw.fromXml(x[k]) for k in std.objectFields(x) }
    else x),
  // The result of a script written in another format than XML: when the input is XML, as DataWeave would write it
  output(result, input):: if isXml(input) then dw.fromXml(result) else result,

  // -- Selectors

  // x.k: null when missing; on an array the field of every element that has it; of an XML element repeated under
  // the same name the first; selRaw keeps an XML element as an object (for its attributes)
  selRaw(x0, k):: strict(x0, function(x)
    if std.isObject(x) then strict(childKey(x, k), function(key) if key == null then null else inScope(x, first(x[key])))
    else if std.isArray(x) then std.flattenArrays([
      strict(e, function(e)
        if !std.isObject(e) then []
        else strict(childKey(e, k), function(key)
          if key == null then [] else if isRepeated(e[key]) then inScope(e, e[key]) else [inScope(e, e[key])]))
      for e in x
    ])
    else null),
  sel(x0, k):: strict(x0, function(x)
    strict(dw.selRaw(x, k), function(v) if std.isArray(x) then std.map(text, v) else text(v))),
  // x.ns#k: the child k in the namespace with the given URI; a child whose prefix is not declared in scope (as when
  // it was not selected from its parent) is taken to be in that namespace
  selNsRaw(x0, uri, k):: strict(x0, function(x)
    local ns = if std.isObject(x) && std.objectHas(x, '@xmlns') then x['@xmlns'] else {};
    local inNamespace(f) =
      local i = std.findSubstr(':', f);
      local prefix = if std.length(i) == 0 then '$' else f[:i[0]];
      strict(first(x[f]), function(v)
        strict(ns + (if isElement(v) && std.objectHas(v, '@xmlns') then v['@xmlns'] else {}), function(declarations)
          !std.objectHas(declarations, prefix) || declarations[prefix] == uri));
    if !isXml(x) then dw.selRaw(x, k)
    else strict([f for f in std.objectFields(x) if !isMeta(f) && !isTextKey(f) && localName(f) == k && inNamespace(f)],
                function(m) if std.length(m) == 0 then null else inScope(x, first(x[m[0]])))),
  selNs(x, uri, k):: text(dw.selNsRaw(x, uri, k)),
  // x.a.b.c
  pathRaw(x, ks):: std.foldl(function(v, k) dw.selRaw(v, k), ks, x),
  path(x, ks):: std.foldl(function(v, k) dw.sel(v, k), ks, x),
  // x.@a
  attr(x0, a):: strict(x0, function(x)
    if std.isArray(x) then [dw.attr(e, a) for e in x]
    else if std.isObject(x) && std.objectHas(x, '@' + a) then x['@' + a]
    else null),
  // x.*k: all the values of k (an XML element repeated under the same name is read as an array)
  multiRaw(x0, k):: strict(x0, function(x)
    if std.isObject(x) then strict(childKey(x, k), function(key)
      if key == null then null else if std.isArray(x[key]) then inScope(x, x[key]) else [inScope(x, x[key])])
    else if std.isArray(x) then
      std.flattenArrays([strict(dw.multiRaw(e, k), function(v) if v == null then [] else v) for e in x])
    else null),
  multi(x, k):: strict(dw.multiRaw(x, k), function(v) if v == null then null else std.map(text, v)),
  // x..k: the values of k at any depth (of an XML element repeated under the same name the first), x..*k all of them
  local descendants(x0, k, all) = strict(x0, function(x)
    local walk(v) =
      if std.isObject(v) then
        strict(childKey(v, k), function(key)
          if key == null then [] else if all && isRepeated(v[key]) then std.map(text, v[key]) else [text(first(v[key]))])
        + std.flattenArrays([walk(v[f]) for f in std.objectFields(v) if !isMeta(f)])
      else if std.isArray(v) then std.flattenArrays([walk(e) for e in v])
      else [];
    if x == null then null else walk(x)),
  desc(x, k):: descendants(x, k, false),
  descAll(x, k):: descendants(x, k, true),
  // x[i]: from the end when negative, null when out of range; x["k"] is x.k
  idx(x0, i):: strict(x0, function(x)
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
    else null),
  // x[a to b]: inclusive, from the end when negative, reversed when a > b
  slice(x0, a, b):: strict(x0, function(x)
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
      else std.reverse(part)),
  // a to b
  range(a, b):: if a <= b then std.range(a, b) else std.reverse(std.range(b, a)),
  // x.k?
  has(x0, k):: strict(x0, function(x) std.isObject(x) && childKey(x, k) != null),

  // -- Values

  default(v0, fallback):: strict(v0, function(v) if v == null then fallback else v),
  // a value in a string (interpolation, dynamic key)
  str(v0):: strict(v0, function(v) if std.isString(v) then v else std.toString(v)),
  toString(v0):: strict(v0, function(v) if v == null || std.isString(v) then v else std.toString(v)),
  nullSafe(f, x0):: strict(x0, function(x) if x == null then null else f(x)),
  // a function given as a value, called with up to three arguments
  fn(f0):: strict(f0, function(f)
    local n = std.length(f);
    if n == 0 then function(a=null, b=null, c=null) f()
    else if n == 1 then function(a, b=null, c=null) f(a)
    else if n == 2 then function(a, b=null, c=null) f(a, b)
    else f),
  typeOf(x0):: strict(x0, function(x)
    if x == null then 'Null'
    else if std.isString(x) then 'String'
    else if std.isNumber(x) then 'Number'
    else if std.isBoolean(x) then 'Boolean'
    else if std.isArray(x) then 'Array'
    else if std.isObject(x) then 'Object'
    else 'Function'),
  // key @(attributes): value, written as XML (a null attribute as "null", or left out with skipNull); the attributes
  // of an element of the input are those of its key in the input, so they are replaced
  withAttributes(v0, attrs0, skipNull=false):: strict(v0, function(v) strict(attrs0, function(attrs)
    local a = { ['@' + k]: dw.str(attrs[k]) for k in std.objectFields(attrs) if !(skipNull && attrs[k] == null) };
    if std.isArray(v) then [dw.withAttributes(e, attrs, skipNull) for e in v]
    else if isElement(v) then withoutAttributes(v) + a + { '~keyAttributes':: true }
    else if std.isObject(v) then v + a
    else if v == null then a
    else a { '$': dw.str(v) })),
  // The DataSonnet XML writer writes the child elements in the order of their position in '~' (else of their field):
  // the elements of an object made by the script are given their position in it, as an element of the input has its
  // position in the input (the output of the script is not read anymore, so a value is written as an element)
  // an element of the input without its attributes (but with its namespace declarations), as the value of a key
  local withoutAttributes(v) =
    std.foldl(function(acc, k) acc + { [k]:: null }, [k for k in std.objectFields(v) if std.startsWith(k, '@') && k != '@xmlns'], v),
  local positioned(v, i) =
    if std.isObject(v) then v { '~': i }
    else if v == null then { '~': i }
    else { '$': dw.str(v), '~': i },
  local ordered(x0) = strict(x0, function(x)
    if std.isArray(x) then std.map(ordered, x)
    else if !std.isObject(x) then x
    // an element of the input selected under a key of the script: written with the attributes of that key (none or
    // those of withAttributes)
    else if isElement(x) then
      (if std.objectHasAll(x, '~selected') && !std.objectHasAll(x, '~keyAttributes') then withoutAttributes(x) else x)
    else
      local fields = [k for k in std.objectFields(x) if !isMeta(k) && !isTextKey(k)];
      // an empty array is written as an empty element
      local count(k) = if std.isArray(x[k]) then std.max(1, std.length(x[k])) else 1;
      local fieldOrder = std.flattenArrays([[k for _ in std.range(1, count(k))] for k in fields]);
      // the keys of the elements in the order of the object, or as kept by xmlObject
      strict(if std.objectHasAll(x, '~order') && std.length(x['~order']) == std.length(fieldOrder)
                && std.all([std.length([o for o in x['~order'] if o == k]) == count(k) for k in fields])
             then x['~order'] else fieldOrder, function(order)
        { [k]: x[k] for k in std.objectFields(x) if isMeta(k) || isTextKey(k) }
        + { [k]: strict([i for i in indexes(order) if order[i] == k], function(ps)
                   if std.isArray(x[k]) && std.length(x[k]) > 0 then [positioned(ordered(x[k][j]), ps[j]) for j in indexes(x[k])]
                   else positioned(if std.isArray(x[k]) then null else ordered(x[k]), ps[0]))
            for k in fields })),
  // The result of a script written as XML: an element of the input (such as payload.Envelope.Body) is written as its
  // content; the namespaces of the header, and those of the input in scope, are declared on the root element
  xmlOutput(doc0, ns):: strict(doc0, function(doc)
    local inherited = if isElement(doc) && std.objectHas(doc, '@xmlns') then doc['@xmlns'] else {};
    strict(if isElement(doc) then { [k]: doc[k] for k in std.objectFields(doc) if !isMeta(k) && !isTextKey(k) } else doc,
           function(content)
             if !std.isObject(content) || std.length(std.objectFields(content)) != 1 then content
             else
               local root = std.objectFields(content)[0];
               strict(ordered(content[root]), function(v)
                 if inherited + ns == {} then { [root]: v }
                 else
                   local own = if std.isObject(v) && std.objectHas(v, '@xmlns') then v['@xmlns'] else {};
                   { [root]: (if std.isObject(v) then v else if v == null then {} else { '$': dw.str(v) })
                             + { '@xmlns': inherited + ns + own } }))),
  // x.@: the attributes of an XML element
  attrs(x0):: strict(x0, function(x) if std.isArray(x) then [attrsOf(e) for e in x] else attrsOf(first(x))),
  // a ~= b
  similar(a0, b0):: strict(a0, function(a) strict(b0, function(b)
    a == b || (a != null && b != null && dw.str(a) == dw.str(b)))),
  // an object spread { (x) }: an object, or an array of objects
  toObject(x0):: strict(x0, function(x)
    if x == null then {}
    else if std.isArray(x) then std.foldl(function(acc, o) acc + dw.toObject(o), x, {})
    else if isXml(x) then fromEntries(entries(x))
    else x),
  // An object written as XML, of its parts (objects of its fields, and object spreads): a key that repeats is an
  // element that repeats (DataWeave keeps every field of a key that repeats, where Jsonnet keeps the last)
  local fieldsOf(x0) = strict(x0, function(x)
    if x == null then []
    else if std.isArray(x) then std.flattenArrays([fieldsOf(e) for e in x])
    else if isElement(x) then [{ k: k, v: x[k] } for k in std.objectFields(x) if !isMeta(k) && !isTextKey(k)]
    else [{ k: k, v: x[k] } for k in std.objectFields(x)]),
  // (the items of an array are elements that repeat); the order of the elements is kept in the hidden field '~order'
  xmlObject(parts):: strict(std.flattenArrays([fieldsOf(p) for p in parts]), function(fs)
    strict(std.flattenArrays([strict(f.v, function(v) if std.isArray(v) then [{ k: f.k, v: e } for e in v]
                                                      else [{ k: f.k, v: v }])
                              for f in fs]), function(es)
      local keys = std.foldl(function(acc, e) if std.member(acc, e.k) then acc else acc + [e.k], es, []);
      { [k]: strict([e.v for e in es if e.k == k], function(vs) if std.length(vs) == 1 then vs[0] else vs) for k in keys }
      + { '~order':: [e.k for e in es] })),
  // obj - "key", array - element
  minus(a0, b):: strict(a0, function(a)
    if std.isObject(a) then fromEntries([e for e in entries(a) if e.k != b])
    else if std.isArray(a) then [e for e in a if e != b]
    else a - b),
  // obj -- ["k1", "k2"] (or an object with the keys), array -- elements
  removeAll(a0, b0):: strict(a0, function(a) strict(b0, function(b)
    strict(if std.isObject(b) then [e.k for e in entries(b)] else b, function(keys)
      if a == null then null
      else if std.isObject(a) then fromEntries([e for e in entries(a) if !std.member(keys, e.k)])
      else [e for e in a if !std.member(keys, e)]))),
  // the skipNullOn writer property: everywhere, objects or arrays (JSON), elements (XML; attributes are left out
  // by withAttributes)
  skipNulls(v0, where):: strict(v0, function(v)
    local objects = where == 'everywhere' || where == 'objects' || where == 'elements';
    local arrays = where == 'everywhere' || where == 'arrays';
    if std.isObject(v) then
      { [k]: dw.skipNulls(v[k], where) for k in std.objectFields(v) if !(objects && v[k] == null) }
      + (if std.objectHasAll(v, '~order') then { '~order':: [k for k in v['~order'] if !(objects && v[k] == null)] }
         else {})
    else if std.isArray(v) then
      std.flattenArrays([strict(e, function(e) if arrays && e == null then [] else [dw.skipNulls(e, where)]) for e in v])
    else v),

  // -- Strings

  upper(s0):: strict(s0, function(s) if s == null then null else std.asciiUpper(s)),
  lower(s0):: strict(s0, function(s) if s == null then null else std.asciiLower(s)),
  trim(s0):: strict(s0, function(s) if s == null then null else std.stripChars(s, ' \t\n\r')),
  isBlank(s0):: strict(s0, function(s) s == null || std.length(std.stripChars(s, ' \t\n\r')) == 0),
  contains(a0, b):: strict(a0, function(a)
    if a == null then false
    else if std.isArray(a) then std.member(a, b)
    else std.length(std.findSubstr(b, a)) > 0),
  // contains /regex/, with scan the ds.scan function
  containsMatch(s0, scan, regex):: strict(s0, function(s) s != null && std.length(scan(s, regex)) > 0),
  // splitBy /regex/, with split the ds.splitBy function
  splitByMatch(s0, split, regex):: strict(s0, function(s) if s == null then null else split(s, regex)),
  startsWith(s0, prefix):: strict(s0, function(s) s != null && std.startsWith(s, prefix)),
  endsWith(s0, suffix):: strict(s0, function(s) s != null && std.endsWith(s, suffix)),
  splitBy(s0, separator):: strict(s0, function(s)
    if s == null then null
    else if separator == '' then std.stringChars(s)
    else std.split(s, separator)),
  joinBy(a0, separator):: strict(a0, function(a)
    if a == null then null else std.join(separator, [dw.str(e) for e in a])),
  replace(s0, target, replacement):: strict(s0, function(s)
    if s == null then null else std.strReplace(s, target, replacement)),
  // replace /regex/ with a function of the match (the matched text and its groups), with starts and matches as given
  // by ds.find and ds.scan
  replaceMatches(s0, starts0, matches0, f):: strict(s0, function(s) strict(starts0, function(starts)
    strict(matches0, function(matches)
      if s == null then null
      else
        strict(std.foldl(function(acc, i) {
          out: acc.out + std.substr(s, acc.pos, starts[i] - acc.pos) + dw.str(f(matches[i])),
          pos: starts[i] + std.length(matches[i][0]),
        }, indexes(matches), { out: '', pos: 0 }), function(r) r.out + std.substr(s, r.pos, std.length(s) - r.pos))))),

  // -- Arrays and objects
  //
  // A function given to them is called with the item evaluated once (an item of an array computed by another
  // function is evaluated each time it is used).

  sizeOf(x0):: strict(x0, function(x)
    if x == null then null else if isXml(x) then std.length(entries(x)) else std.length(text(x))),
  isEmpty(x0):: strict(x0, function(x)
    x == null || strict(text(x), function(t) t == null || std.length(if isXml(x) then entries(x) else t) == 0)),
  map(a0, f):: strict(a0, function(a)
    if a == null then null else std.mapWithIndex(function(i, x) strict(x, function(x) f(x, i)), a)),
  filter(a0, f):: strict(a0, function(a)
    if a == null then null
    else if std.isString(a) then (
      local cs = std.stringChars(a);
      std.join('', [cs[i] for i in indexes(cs) if f(cs[i], i)])
    )
    else if std.isObject(a) then dw.filterObject(a, function(v, k, i) f(v, k))
    else std.flattenArrays([strict(a[i], function(x) if f(x, i) then [x] else []) for i in indexes(a)])),
  flatMap(a, f):: strict(dw.map(a, f), function(m) if m == null then null else std.flattenArrays(m)),
  // reduce with an initial accumulator, and without (starting with the first item)
  reduce(a0, f, init):: strict(a0, function(a)
    if a == null then null else std.foldl(function(acc, x) strict(x, function(x) f(x, acc)), a, init)),
  reduce1(a0, f):: strict(a0, function(a)
    if a == null || std.length(a) == 0 then null
    else std.foldl(function(acc, x) strict(x, function(x) f(x, acc)), a[1:], a[0])),
  distinctBy(a0, f):: strict(a0, function(a)
    if a == null then null
    else if std.isObject(a) then
      fromEntries(std.foldl(function(acc, e)
                              strict(f(e.v, e.k), function(g)
                                if std.member(acc.keys, g) then acc else { keys: acc.keys + [g], es: acc.es + [e] }),
                            entries(a), { keys: [], es: [] }).es)
    else
      std.foldl(function(acc, i)
                  strict(a[i], function(x) strict(f(x, i), function(g)
                    if std.member(acc.keys, g) then acc else { keys: acc.keys + [g], items: acc.items + [x] })),
                indexes(a), { keys: [], items: [] }).items),
  groupBy(a0, f):: strict(a0, function(a)
    if a == null then null
    else if std.isObject(a) then
      strict([e { g: dw.str(f(e.v, e.k)) } for e in entries(a)], function(es)
        { [g]: fromEntries([e for e in es if e.g == g])
          for g in std.foldl(function(acc, e) if std.member(acc, e.g) then acc else acc + [e.g], es, []) })
    else
      std.foldl(function(acc, i)
                  strict(a[i], function(x) strict(dw.str(f(x, i)), function(g)
                    acc { [g]: (if std.objectHas(acc, g) then acc[g] else []) + [x] })),
                indexes(a), {})),
  // a stable sort by key, with the key of each item computed once (std.sort of DataSonnet ignores keyF for an array
  // of numbers or strings, so it sorts objects with the indexes)
  local sortBy(xs0, key) = strict(xs0, function(xs) {
    ks: { [std.toString(i)]: key(xs[i], i) for i in indexes(xs) },
    r: [xs[e.i] for e in std.sort([{ i: i } for i in indexes(xs)], function(e) self.ks[std.toString(e.i)])],
  }.r),
  orderBy(a0, f):: strict(a0, function(a)
    if a == null then null
    else if std.isObject(a) then fromEntries(sortBy(entries(a), function(e, i) f(e.v, e.k)))
    else sortBy(a, function(x, i) strict(x, function(x) f(x, i)))),
  mapObject(o0, f):: strict(o0, function(o)
    if o == null then null
    else strict(entries(o), function(es)
      fromEntries(std.flattenArrays([strict(es[i], function(e) entries(f(e.v, e.k, i))) for i in indexes(es)])))),
  filterObject(o0, f):: strict(o0, function(o)
    if o == null then null
    else strict(entries(o), function(es)
      fromEntries(std.flattenArrays([strict(es[i], function(e) if f(e.v, e.k, i) then [e] else [])
                                     for i in indexes(es)])))),
  pluck(o0, f):: strict(o0, function(o)
    if o == null then null
    else strict(entries(o), function(es) [strict(es[i], function(e) f(e.v, e.k, i)) for i in indexes(es)])),
  keysOf(o0):: strict(o0, function(o) if o == null then null else [e.k for e in entries(o)]),
  namesOf(o):: dw.keysOf(o),
  valuesOf(o0):: strict(o0, function(o) if o == null then null else [e.v for e in entries(o)]),
  entriesOf(o0):: strict(o0, function(o)
    if o == null then null else [{ key: e.k, value: e.v, attributes: e.a } for e in entries(o)]),
  flatten(a0):: strict(a0, function(a)
    if a == null then null else std.flattenArrays([strict(e, function(e) if std.isArray(e) then e else [e]) for e in a])),
  zip(a0, b0):: strict(a0, function(a) strict(b0, function(b)
    if a == null || b == null then null
    else [[a[i], b[i]] for i in std.range(0, std.min(std.length(a), std.length(b)) - 1)])),
  // x then f
  pipe(v, f):: strict(v, f),
  sum(a0):: strict(a0, function(a) if a == null then null else std.foldl(function(acc, x) acc + x, a, 0)),
  avg(a0):: strict(a0, function(a) if a == null then null else dw.sum(a) / std.length(a)),
  min(a0):: strict(a0, function(a)
    if a == null || std.length(a) == 0 then null
    else std.foldl(function(m, x) strict(x, function(x) if x < m then x else m), a[1:], a[0])),
  max(a0):: strict(a0, function(a)
    if a == null || std.length(a) == 0 then null
    else std.foldl(function(m, x) strict(x, function(x) if x > m then x else m), a[1:], a[0])),
  maxBy(a0, f):: strict(a0, function(a)
    if a == null || std.length(a) == 0 then null
    else std.foldl(function(m, x) strict(x, function(x) if f(x) > f(m) then x else m), a[1:], a[0])),
  minBy(a0, f):: strict(a0, function(a)
    if a == null || std.length(a) == 0 then null
    else std.foldl(function(m, x) strict(x, function(x) if f(x) < f(m) then x else m), a[1:], a[0])),
  sumBy(a0, f):: strict(a0, function(a) if a == null then null else std.foldl(function(acc, x) acc + f(x), a, 0)),
  countBy(a0, f):: strict(a0, function(a) if a == null then null else std.length([x for x in a if f(x)])),
  every(a0, f):: strict(a0, function(a) a == null || std.all([f(x) for x in a])),
  some(a0, f):: strict(a0, function(a) a != null && std.any([f(x) for x in a])),
  firstWith(a0, f):: strict(a0, function(a)
    if a == null then null
    else strict([x for x in a if f(x)], function(m) if std.length(m) == 0 then null else m[0])),
  partition(a0, f):: strict(a0, function(a)
    if a == null then null else { success: [x for x in a if f(x)], failure: [x for x in a if !f(x)] }),
  indexOf(a0, x):: strict(a0, function(a)
    if a == null then -1
    else strict(if std.isString(a) then std.findSubstr(x, a) else [i for i in indexes(a) if a[i] == x],
                function(found) if std.length(found) == 0 then -1 else found[0])),
  lastIndexOf(a0, x):: strict(a0, function(a)
    if a == null then -1
    else strict(if std.isString(a) then std.findSubstr(x, a) else [i for i in indexes(a) if a[i] == x],
                function(found) if std.length(found) == 0 then -1 else found[std.length(found) - 1])),
  take(a0, n):: strict(a0, function(a) if a == null then null else a[0:std.max(0, std.min(n, std.length(a)))]),
  drop(a0, n):: strict(a0, function(a) if a == null then null else a[std.max(0, std.min(n, std.length(a))):]),
  splitAt(a0, n):: strict(a0, function(a) if a == null then null else { l: dw.take(a, n), r: dw.drop(a, n) }),
  divideBy(a0, n):: strict(a0, function(a)
    if a == null then null else [a[i:std.min(i + n, std.length(a))] for i in std.range(0, std.length(a) - 1) if i % n == 0]),

  // -- Numbers

  abs(x0):: strict(x0, function(x) if x == null then null else if x < 0 then -x else x),
  // half up, as DataWeave
  round(x0):: strict(x0, function(x) if x == null then null else if x < 0 then -std.floor(-x + 0.5) else std.floor(x + 0.5)),
  isEven(x):: x % 2 == 0,
  isOdd(x):: x % 2 != 0,
  isInteger(x0):: strict(x0, function(x) std.isNumber(x) && std.floor(x) == x),
  isDecimal(x0):: strict(x0, function(x) std.isNumber(x) && std.floor(x) != x),
}
