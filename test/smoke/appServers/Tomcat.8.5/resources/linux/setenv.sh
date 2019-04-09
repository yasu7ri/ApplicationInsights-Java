#!/usr/bin/env bash

if [[ ! -z "$APACHE_LOG_HTTP" ]] || [[ ! -z "$APACHE_LOG_WIRE" ]] || [[ ! -z "$APACHE_LOG_CONN" ]] || [[ ! -z "$APACHE_LOG_CLIENT" ]]; then
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.Log=com.microsoft.applicationinsights.core.dependencies.apachecommons.logging.impl.SimpleLog"
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.showdatetime=true"
fi

if [ ! -z "$APACHE_LOG_HTTP" ]; then
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.org.apache.http=$APACHE_LOG_HTTP"
fi

if [ ! -z "$APACHE_LOG_WIRE" ]; then
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.org.apache.http.wire=$APACHE_LOG_WIRE"
fi

if [ ! -z "$APACHE_LOG_CONN" ]; then
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.org.apache.http.impl.conn=$APACHE_LOG_CONN"
fi

if [ ! -z "$APACHE_LOG_CLIENT" ]; then
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.org.apache.http.impl.client=$APACHE_LOG_CLIENT"
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.org.apache.http.client=$APACHE_LOG_CLIENT"
fi

if [ ! -z "$AI_AGENT_MODE" ]; then
    CATALINA_OPTS="-javaagent:/root/docker-stage/aiagent/$AGENT_JAR_NAME $CATALINA_OPTS"
fi