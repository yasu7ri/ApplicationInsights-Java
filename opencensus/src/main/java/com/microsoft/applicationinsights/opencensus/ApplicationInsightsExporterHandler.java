/*
 * Copyright 2018, OpenCensus Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microsoft.applicationinsights.opencensus;

import com.google.common.annotations.VisibleForTesting;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.extensibility.context.OperationContext;
import com.microsoft.applicationinsights.telemetry.Duration;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import io.opencensus.common.Function;
import io.opencensus.common.Functions;
import io.opencensus.common.Timestamp;
import io.opencensus.trace.Annotation;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Link;
import io.opencensus.trace.Span.Kind;
import io.opencensus.trace.Tracestate.Entry;
import io.opencensus.trace.export.SpanData;
import io.opencensus.trace.export.SpanExporter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

@VisibleForTesting
final class ApplicationInsightsExporterHandler extends SpanExporter.Handler {

  private static final String LINK_PROPERTY_NAME = "link";
  private static final String LINK_SPAN_ID_PROPERTY_NAME = "spanId";
  private static final String LINK_TRACE_ID_PROPERTY_NAME = "traceId";

  private static final String STATUS_DESCRIPTION_KEY = "statusDescription";

  private static final String HTTP_CODE = "http.status_code";
  private static final String HTTP_USER_AGENT = "http.user_agent";
  private static final String HTTP_ROUTE = "http.route";
  private static final String HTTP_PATH = "http.path";
  private static final String HTTP_URL = "http.url";
  private static final String HTTP_METHOD = "http.method";
  private static final String HTTP_HOST = "http.host";

  private static final Function<Object, String> RETURN_STRING =
      new Function<Object, String>() {
        @Override
        public String apply(Object input) {
          return input.toString();
        }
      };

  private final TelemetryClient telemetryClient;

  public ApplicationInsightsExporterHandler(TelemetryClient telemetryClient) {
    this.telemetryClient = telemetryClient;
  }

  private static String attributeValueToString(AttributeValue attributeValue) {
    return attributeValue.match(
        RETURN_STRING, RETURN_STRING, RETURN_STRING, RETURN_STRING, Functions.<String>returnNull());
  }

  @Override
  public void export(Collection<SpanData> spanDataList) {
    for (SpanData span : spanDataList) {
      try {
        exportSpan(span);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  private void exportSpan(SpanData span) {
    Telemetry telemetry = null;

    if (span.getKind() == Kind.CLIENT) {
      RemoteDependencyTelemetry dependency = trackDependencyFromSpan(span);
      telemetry = dependency;
    } else {
      RequestTelemetry request = trackRequestFromSpan(span);
      telemetry = request;
    }

    if (telemetry != null) {
      // if dependency is track call to AppInsights, telemetry can be null

      String id = setOperationContext(span, telemetry);

      telemetry.setTimestamp(getDate(span.getStartTimestamp()));

      setLinks(span.getLinks(), telemetry.getProperties());

      String operationId = telemetry.getContext().getOperation().getId();
      for (SpanData.TimedEvent<Annotation> annotation : span.getAnnotations().getEvents()) {
        trackTraceFromAnnotation(annotation, operationId, id);
      }

      telemetryClient.track(telemetry);
    }
  }

  private RequestTelemetry trackRequestFromSpan(SpanData span) {
    RequestTelemetry request = new RequestTelemetry();

    request.setDuration(getDuration(span.getStartTimestamp(), span.getEndTimestamp()));
    request.setSuccess(span.getStatus().isOk());

    String host = null;
    String method = null;
    String path = null;
    String route = null;
    String url = null;
    boolean isResultSet = false;

    for (Map.Entry<String, AttributeValue> entry :
        span.getAttributes().getAttributeMap().entrySet()) {
      switch (entry.getKey()) {
        case HTTP_CODE:
          request.setResponseCode(attributeValueToString(entry.getValue()));
          isResultSet = true;
          break;
        case HTTP_USER_AGENT:
          request.getContext().getUser().setUserAgent(attributeValueToString(entry.getValue()));
          break;
        case HTTP_ROUTE:
          route = attributeValueToString(entry.getValue());
          break;
        case HTTP_PATH:
          path = attributeValueToString(entry.getValue());
          break;
        case HTTP_METHOD:
          method = attributeValueToString(entry.getValue());
          break;
        case HTTP_HOST:
          host = attributeValueToString(entry.getValue());
          break;
        case HTTP_URL:
          url = attributeValueToString(entry.getValue());
          break;
        default:
          if (!request.getProperties().containsKey(entry.getKey())) {
            request.getProperties().put(entry.getKey(), attributeValueToString(entry.getValue()));
          }
      }
    }

    trySetHttpProperties(method, url, host, path, route, request);
    if (request.getName() == null) {
      request.setName(span.getName());
    }

    if (!isResultSet) {
      request.setResponseCode(span.getStatus().getCanonicalCode().toString());
      if (span.getStatus().getDescription() != null) {
        request.getProperties().put(STATUS_DESCRIPTION_KEY, span.getStatus().getDescription());
      }
    }

    return request;
  }

  private RemoteDependencyTelemetry trackDependencyFromSpan(SpanData span) {
    String url = null;
    if (span.getAttributes().getAttributeMap().containsKey(HTTP_URL)) {
      url = attributeValueToString(span.getAttributes().getAttributeMap().get(HTTP_URL));
      if (isApplicationInsightsUrl(url)) {
        return null;
      }
    }

    RemoteDependencyTelemetry dependency = new RemoteDependencyTelemetry();

    dependency.setDuration(getDuration(span.getStartTimestamp(), span.getEndTimestamp()));
    dependency.setSuccess(span.getStatus().isOk());

    String method = null;
    String path = null;
    String host = null;

    boolean isHttp = false;
    boolean isResultSet = false;

    for (Map.Entry<String, AttributeValue> entry :
        span.getAttributes().getAttributeMap().entrySet()) {
      switch (entry.getKey()) {
        case HTTP_CODE:
          dependency.setResultCode(attributeValueToString(entry.getValue()));
          isHttp = true;
          isResultSet = true;
          break;
        case HTTP_USER_AGENT:
          dependency.getContext().getUser().setUserAgent(attributeValueToString(entry.getValue()));
          break;
        case HTTP_PATH:
          path = attributeValueToString(entry.getValue());
          isHttp = true;
          break;
        case HTTP_METHOD:
          method = attributeValueToString(entry.getValue());
          isHttp = true;
          break;
        case HTTP_HOST:
          dependency.setTarget(attributeValueToString(entry.getValue()));
          break;
        case HTTP_URL:
          url = attributeValueToString(entry.getValue());
          break;
        default:
          if (!dependency.getProperties().containsKey(entry.getKey())) {
            dependency
                .getProperties()
                .put(entry.getKey(), attributeValueToString(entry.getValue()));
          }
      }
    }
    if (isHttp) {
      dependency.setType("Http");
      trySetHttpProperties(method, url, host, path, dependency);
    } else {
      dependency.setName(span.getName());
    }

    if (!isResultSet) {
      dependency.setResultCode(span.getStatus().getCanonicalCode().name());
      if (span.getStatus().getDescription() != null) {
        dependency.getProperties().put(STATUS_DESCRIPTION_KEY, span.getStatus().getDescription());
      }
    }

    return dependency;
  }

  private void trackTraceFromAnnotation(
      SpanData.TimedEvent<Annotation> annotationEvent, String operationId, String parentId) {

    // will be changed in OpenTelemetry

    Annotation annotation = annotationEvent.getEvent();
    TraceTelemetry trace = new TraceTelemetry();

    trace.getContext().getOperation().setId(operationId);
    trace.getContext().getOperation().setParentId(parentId);

    trace.setMessage(annotation.getDescription());
    trace.setTimestamp(getDate(annotationEvent.getTimestamp()));
    setAttributes(annotation.getAttributes(), trace.getProperties());

    telemetryClient.trackTrace(trace);
  }

  private void setLinks(SpanData.Links spanLinks, Map<String, String> telemetryProperties) {
    // for now, we just put links to telemetry properties
    // link0_spanId = ...
    // link0_traceId = ...
    // link0_<attributeKey> = <attributeValue>
    // this is not convenient for querying data
    // We'll consider adding Links to operation telemetry schema
    Link[] links = spanLinks.getLinks().toArray(new Link[0]);
    for (int i = 0; i < links.length; i++) {
      String prefix = String.format("%s%d_", LINK_PROPERTY_NAME, i);
      telemetryProperties.put(
          prefix + LINK_SPAN_ID_PROPERTY_NAME, links[i].getSpanId().toLowerBase16());
      telemetryProperties.put(
          prefix + LINK_TRACE_ID_PROPERTY_NAME, links[i].getTraceId().toLowerBase16());

      for (Map.Entry<String, AttributeValue> entry : links[i].getAttributes().entrySet()) {
        if (!telemetryProperties.containsKey(entry.getKey())) {
          telemetryProperties.put(
              prefix + entry.getKey(), attributeValueToString(entry.getValue()));
        }
      }
    }
  }

  private String generateId(String traceId, String spanId){
    return "|" + traceId + "." + spanId + ".";
  }

  private String setOperationContext(SpanData span, Telemetry telemetry) {
    assert (telemetry instanceof RequestTelemetry || telemetry instanceof  RemoteDependencyTelemetry);

    OperationContext context = telemetry.getContext().getOperation();

    String root = span.getContext().getTraceId().toLowerBase16();
    if (span.getParentSpanId() != null) {
      context.setParentId(generateId(root, span.getParentSpanId().toLowerBase16()));
    }

    context.setId(root);
    String id = generateId(root, span.getContext().getSpanId().toLowerBase16());

    if (telemetry instanceof RemoteDependencyTelemetry) {
      RemoteDependencyTelemetry dependency = (RemoteDependencyTelemetry) telemetry;
      dependency.setId(id);
    } else if (telemetry instanceof RequestTelemetry) {
      RequestTelemetry request = (RequestTelemetry) telemetry;
      request.setId(id);
    }

    // tracestate
    Map<String, String> telemetryProperties = telemetry.getProperties();
    for (Entry entry : span.getContext().getTracestate().getEntries()) {
      if (!telemetryProperties.containsKey(entry.getKey())) {
        telemetryProperties.put(entry.getKey(), entry.getValue());
      }
    }

    return id;
  }

  private void setAttributes(
      Map<String, AttributeValue> attributes, Map<String, String> telemetryProperties) {
    for (Map.Entry<String, AttributeValue> entry : attributes.entrySet()) {
      if (!telemetryProperties.containsKey(entry.getKey())) {
        telemetryProperties.put(entry.getKey(), attributeValueToString(entry.getValue()));
      }
    }
  }

  private Date getDate(Timestamp timestamp) {
    return new Date(timestamp.getSeconds() * 1000 + timestamp.getNanos() / 1000000);
  }

  private Duration getDuration(Timestamp start, Timestamp stop) {
    io.opencensus.common.Duration ocDuration = stop.subtractTimestamp(start);
    return new Duration(ocDuration.getSeconds() * 1000 + ocDuration.getNanos() / 1000000);
  }

  private boolean trySetHttpProperties(String method, String url, String host, String path, String route, RequestTelemetry request) {

    if (url == null &&
        method == null &&
        host == null &&
        path == null &&
        route == null) {
      return false;
    }

    if (url != null) {
      try {
        URL requestUrl = new URL(url);
        request.setUrl(requestUrl);

        if (path == null) {
          path = requestUrl.getPath();
        }
      } catch (MalformedURLException e){
        // ignore
      }
    }

    if (path == null) {
      path = "/";
    }

    if (method != null) {
      request.setName(method + " " + (route != null ? route : path));
    }

    return true;
  }

  private boolean trySetHttpProperties(String method, String url, String host, String path, RemoteDependencyTelemetry dependency) {

    if (url == null &&
        method == null &&
        host == null &&
        path == null) {
      return false;
    }

    if (url != null) {
      dependency.setCommandName(url);

      if (host == null) {
        try {
          URL requestUrl = new URL(url);
          if (host == null) {
            host = requestUrl.getAuthority();
          }

          if (path == null) {
            path = requestUrl.getPath();
          }
        } catch (MalformedURLException e){
          // ignore
        }
      }
    }

    if (path == null) {
      path = "/";
    }

    dependency.setTarget(host);

    if (method != null && path != null) {
      dependency.setName(method + " " + path);
    }

    return true;
  }

  private boolean isApplicationInsightsUrl(String url) {
    return url.startsWith("https://dc.services.visualstudio.com")
        || url.startsWith("https://rt.services.visualstudio.com");
  }
}
