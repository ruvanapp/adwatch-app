#!/bin/sh

##############################################################################
##
##  Gradle start up script for POSIX generated-compatible wrapper
##
##############################################################################

APP_HOME=$(cd "${0%/*}" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ ! -x "$JAVACMD" ] ; then
    echo "ERROR: Java not found. Please set JAVA_HOME."
    exit 1
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"