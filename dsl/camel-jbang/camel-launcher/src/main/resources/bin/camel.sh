#!/bin/sh
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

PRGDIR=`dirname "$PRG"`
BASEDIR=`cd "$PRGDIR" >/dev/null; pwd`

# Reset the REPO variable. If you need to influence this use the environment setup file.
REPO=

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
case "`uname`" in
  CYGWIN*) cygwin=true ;;
esac

# For Cygwin, ensure JAVA_HOME is in UNIX format before it is probed.
if $cygwin ; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
fi

# --- Shared Java 17+ discovery contract ---
# Candidates are tried in order: JAVACMD, $JAVA_HOME/bin/java, java on PATH, CAMEL_FALLBACK_JAVA.
# ($JAVA_HOME is also how SDKMAN's java candidate exposes its runtime.)
# Each must exist, be executable, and report a Java major version >= CAMEL_MIN_JAVA.
# Probe output is captured and never printed during a normal invocation.
CAMEL_MIN_JAVA=17

# Echo the Java major version of the launcher in $1, or nothing if it cannot be determined.
camel_java_major() {
  _cjm_out=`"$1" -version </dev/null 2>&1` || return 1
  _cjm_ver=`echo "$_cjm_out" | sed -n 's/.*version "\([0-9][0-9.]*\).*/\1/p' | head -n 1`
  [ -n "$_cjm_ver" ] || return 1
  case "$_cjm_ver" in
    1.*) echo "$_cjm_ver" | cut -d. -f2 ;;
    *)   echo "$_cjm_ver" | cut -d. -f1 ;;
  esac
}

# Return 0 and set JAVACMD when $1 is an existing, executable, Java >= CAMEL_MIN_JAVA launcher.
camel_try_java() {
  [ -n "$1" ] || return 1
  [ -x "$1" ] || return 1
  _ctj_major=`camel_java_major "$1"` || return 1
  [ -n "$_ctj_major" ] || return 1
  { [ "$_ctj_major" -ge "$CAMEL_MIN_JAVA" ]; } 2>/dev/null || return 1
  JAVACMD="$1"
  return 0
}

if camel_try_java "$JAVACMD"; then
  :
elif [ -n "$JAVA_HOME" ] && camel_try_java "$JAVA_HOME/bin/java"; then
  :
elif _camel_path_java=`command -v java 2>/dev/null` && camel_try_java "$_camel_path_java"; then
  :
elif camel_try_java "$CAMEL_FALLBACK_JAVA"; then
  :
else
  echo "Error: no suitable Java runtime found. Camel CLI requires Java $CAMEL_MIN_JAVA or newer." 1>&2
  echo "Checked the following sources (in order):" 1>&2
  echo "  1. JAVACMD             = ${JAVACMD:-<unset>}" 1>&2
  echo "  2. JAVA_HOME/bin/java  = ${JAVA_HOME:+$JAVA_HOME/bin/java}" 1>&2
  echo "  3. java on PATH        = ${_camel_path_java:-<not found>}" 1>&2
  echo "  4. CAMEL_FALLBACK_JAVA = ${CAMEL_FALLBACK_JAVA:-<unset>}" 1>&2
  echo "Install a Java $CAMEL_MIN_JAVA+ runtime and either add it to PATH, set JAVA_HOME, or set JAVACMD." 1>&2
  exit 1
fi

if [ -z "$REPO" ]
then
  REPO="$BASEDIR"
fi

# Set the classpath to the JAR file
for f in "$BASEDIR"/camel-launcher-*.jar; do
  if [ -f "$f" ]; then
    CLASSPATH="$f"
    break
  fi
done

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
  [ -n "$HOME" ] && HOME=`cygpath --path --windows "$HOME"`
  [ -n "$BASEDIR" ] && BASEDIR=`cygpath --path --windows "$BASEDIR"`
  [ -n "$REPO" ] && REPO=`cygpath --path --windows "$REPO"`
fi

# Set JVM options if specified
if [ -z "$JAVA_OPTS" ]; then
  JAVA_OPTS="-Xmx512m"
fi

exec "$JAVACMD" $JAVA_OPTS -jar "$CLASSPATH" "$@"
