#!/usr/bin/env bash


#FIXME forcing log levels; remove when mechanism works
APACHE_LOG_HTTP=DEBUG
APACHE_LOG_WIRE=ERROR
APACHE_LOG_CONN=ERROR
APACHE_LOG_CLIENT=ERROR

#logging.level.com.microsoft.applicationinsights.core.dependencies.http=DEBUG

if [ ! -z "$APACHE_LOG_HTTP" -o ! -z "$APACHE_LOG_WIRE" -o ! -z "$APACHE_LOG_CONN" -o ! -z "$APACHE_LOG_CLIENT" ]; then
    echo "apache logging enabled"
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.Log=com.microsoft.applicationinsights.core.dependencies.apachecommons.logging.impl.SimpleLog"
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.showdatetime=true"
fi

if [ ! -z "$APACHE_LOG_HTTP" ]; then
    echo "apache logging enabled: http=$APACHE_LOG_HTTP"
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.com.microsoft.applicationinsights.core.dependencies.http=$APACHE_LOG_HTTP"
fi

if [ ! -z "$APACHE_LOG_WIRE" ]; then
    echo "apache logging enabled: wire=$APACHE_LOG_WIRE"
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.com.microsoft.applicationinsights.core.dependencies.http.wire=$APACHE_LOG_WIRE"
fi

if [ ! -z "$APACHE_LOG_CONN" ]; then
    echo "apache logging enabled: conn=$APACHE_LOG_CONN"
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.com.microsoft.applicationinsights.core.dependencies.http.impl.conn=$APACHE_LOG_CONN"
fi

if [ ! -z "$APACHE_LOG_CLIENT" ]; then
    echo "apache logging enabled: client=$APACHE_LOG_CLIENT"
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.com.microsoft.applicationinsights.core.dependencies.http.impl.client=$APACHE_LOG_CLIENT"
    CATALINA_OPTS="$CATALINA_OPTS -Dcom.microsoft.applicationinsights.core.dependencies.apachecommons.logging.simplelog.log.com.microsoft.applicationinsights.core.dependencies.http.client=$APACHE_LOG_CLIENT"
fi

if [ ! -z "$AI_AGENT_MODE" ]; then
    CATALINA_OPTS="-javaagent:/root/docker-stage/aiagent/$AGENT_JAR_NAME $CATALINA_OPTS"
fi