#!/bin/sh
# Gradle wrapper script

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Get the fully qualified path to the script
SCRIPT=$(readlink -f "$0") 2>/dev/null
# if readlink doesn't work, use realpath
if [ -z "$SCRIPT" ]; then
    SCRIPT=$(realpath "$0") 2>/dev/null
fi
# fallback to $0
if [ -z "$SCRIPT" ]; then
    SCRIPT="$0"
fi

SCRIPT_DIR=$(dirname "$SCRIPT")
APP_HOME=$SCRIPT_DIR

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
