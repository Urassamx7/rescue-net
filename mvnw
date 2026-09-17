#!/bin/sh
# Apache Maven Wrapper startup script, version 3.3.2
set -e

basedir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
wrapper_jar="$basedir/.mvn/wrapper/maven-wrapper.jar"
wrapper_url="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

if [ -f "$basedir/.mvn/wrapper/maven-wrapper.properties" ]; then
  while IFS='=' read -r key value; do
    case "$key" in
      wrapperUrl) wrapper_url=$value ;;
    esac
  done < "$basedir/.mvn/wrapper/maven-wrapper.properties"
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_cmd="$JAVA_HOME/bin/java"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java.exe" ]; then
  java_cmd="$JAVA_HOME/bin/java.exe"
else
  java_cmd=$(command -v java || true)
fi

if [ -z "$java_cmd" ]; then
  echo "Error: JAVA_HOME not found. Install JDK 26 and set JAVA_HOME." >&2
  exit 1
fi

if [ ! -f "$wrapper_jar" ]; then
  echo "Downloading Maven Wrapper..."
  mkdir -p "$basedir/.mvn/wrapper"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$wrapper_url" -o "$wrapper_jar"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$wrapper_url" -O "$wrapper_jar"
  else
    echo "Need curl or wget to download Maven Wrapper." >&2
    exit 1
  fi
fi

exec "$java_cmd" $MAVEN_OPTS -classpath "$wrapper_jar" \
  "-Dmaven.multiModuleProjectDirectory=$basedir" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
