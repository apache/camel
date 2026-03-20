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
// Apache Camel standard library for DataSonnet
// Auto-discovered from the classpath. Import with: local c = import 'camel.libsonnet';
{
  // String helpers
  capitalize(s):: std.asciiUpper(s[0]) + s[1:],
  trim(s):: std.stripChars(s, " \t\n\r"),
  split(s, sep):: std.split(s, sep),
  join(arr, sep):: std.join(sep, arr),
  contains(s, sub):: std.length(std.findSubstr(sub, s)) > 0,
  startsWith(s, prefix):: std.length(s) >= std.length(prefix) && s[:std.length(prefix)] == prefix,
  endsWith(s, suffix):: std.length(s) >= std.length(suffix) && s[std.length(s) - std.length(suffix):] == suffix,
  replace(s, old, new):: std.strReplace(s, old, new),
  lower(s):: std.asciiLower(s),
  upper(s):: std.asciiUpper(s),

  // Collection helpers
  sum(arr):: std.foldl(function(acc, x) acc + x, arr, 0),
  sumBy(arr, f):: std.foldl(function(acc, x) acc + f(x), arr, 0),
  first(arr):: if std.length(arr) > 0 then arr[0] else null,
  last(arr):: if std.length(arr) > 0 then arr[std.length(arr) - 1] else null,
  count(arr):: std.length(arr),
  distinct(arr):: std.foldl(
    function(acc, x) if std.member(acc, x) then acc else acc + [x],
    arr, []
  ),
  flatMap(arr, f):: std.flatMap(f, arr),
  sortBy(arr, f):: std.sort(arr, keyF=f),
  groupBy(arr, f):: std.foldl(
    function(acc, x)
      local k = f(x);
      acc + (
        if std.objectHas(acc, k)
        then { [k]: acc[k] + [x] }
        else { [k]: [x] }
      ),
    arr, {}
  ),
  min(arr):: std.foldl(function(acc, x) if acc == null || x < acc then x else acc, arr, null),
  max(arr):: std.foldl(function(acc, x) if acc == null || x > acc then x else acc, arr, null),
  zip(a, b):: std.mapWithIndex(function(i, x) [x, b[i]], a),
  take(arr, n):: arr[:n],
  drop(arr, n):: arr[n:],

  // Object helpers
  pick(obj, keys):: { [k]: obj[k] for k in keys if std.objectHas(obj, k) },
  omit(obj, keys):: { [k]: obj[k] for k in std.objectFields(obj) if !std.member(keys, k) },
  merge(a, b):: a + b,
  entries(obj):: [{ key: k, value: obj[k] } for k in std.objectFields(obj)],
  fromEntries(arr):: { [e.key]: e.value for e in arr },
  keys(obj):: std.objectFields(obj),
  values(obj):: [obj[k] for k in std.objectFields(obj)],
}
