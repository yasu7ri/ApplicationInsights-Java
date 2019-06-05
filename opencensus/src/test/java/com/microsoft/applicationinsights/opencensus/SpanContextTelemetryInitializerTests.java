package com.microsoft.applicationinsights.opencensus;

import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.TraceOptions;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracestate;
import io.opencensus.trace.Tracing;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Assert;
import org.junit.Test;

public final class SpanContextTelemetryInitializerTests {

  @Test
  public void noopWithoutCurrentSpan()
  {
    TraceTelemetry trace = new TraceTelemetry();
    SpanContextTelemetryInitializer initializer = new SpanContextTelemetryInitializer();
    initializer.initialize(trace);
    Assert.assertNull(trace.getContext().getOperation().getId());
    Assert.assertNull(trace.getContext().getOperation().getParentId());
  }

  @Test
  public void noopWithOperationIdSet()
  {
    TraceTelemetry trace = new TraceTelemetry();
    trace.getContext().getOperation().setId("foo");
    trace.getContext().getOperation().setParentId("bar");

    SpanContextTelemetryInitializer initializer = new SpanContextTelemetryInitializer();
    Tracer tracer = Tracing.getTracer();
    Span span = tracer.spanBuilder("foo").startSpan();
    try (Scope s = tracer.withSpan(span))
    {
      initializer.initialize(trace);
    }

    Assert.assertEquals("foo", trace.getContext().getOperation().getId());
    Assert.assertEquals("bar", trace.getContext().getOperation().getParentId());
  }

  @Test
  public void setsContextFromCurrentSpan()
  {
    TraceTelemetry trace = new TraceTelemetry();
    SpanContextTelemetryInitializer initializer = new SpanContextTelemetryInitializer();

    Tracer tracer = Tracing.getTracer();
    Span span = tracer.spanBuilder("foo").startSpan();
    try (Scope s = tracer.withSpan(span))
    {
      initializer.initialize(trace);
    }

    String expectedTraceId = span.getContext().getTraceId().toLowerBase16();
    String expectedSpanId = span.getContext().getSpanId().toLowerBase16();
    Assert.assertEquals(expectedTraceId, trace.getContext().getOperation().getId());
    Assert.assertEquals("|" + expectedTraceId + "." + expectedSpanId + ".",
        trace.getContext().getOperation().getParentId());
  }

  @Test
  public void setsTracestateOnRequestsAndDependenciesFromCurrentSpan()
  {
    TraceTelemetry trace = new TraceTelemetry();
    RequestTelemetry request = new RequestTelemetry();
    RemoteDependencyTelemetry dependency = new RemoteDependencyTelemetry();
    ExceptionTelemetry exception = new ExceptionTelemetry(new Exception());

    SpanContextTelemetryInitializer initializer = new SpanContextTelemetryInitializer();

    Tracer tracer = Tracing.getTracer();
    SpanContext parent = SpanContext.create(
        TraceId.generateRandomId(ThreadLocalRandom.current()),
        SpanId.generateRandomId(ThreadLocalRandom.current()),
        TraceOptions.DEFAULT,
        Tracestate.builder()
          .set("k1", "v1")
          .set("k2", "v2")
          .build());

    Span span = tracer.spanBuilderWithRemoteParent("foo", parent).startSpan();
    span.getContext().getTracestate();
    try (Scope s = tracer.withSpan(span))
    {
      initializer.initialize(trace);
      initializer.initialize(request);
      initializer.initialize(dependency);
      initializer.initialize(exception);
    }

    Assert.assertFalse(trace.getProperties().containsKey("tracestate"));
    Assert.assertFalse(exception.getProperties().containsKey("tracestate"));
    Assert.assertTrue(request.getProperties().containsKey("k1"));
    Assert.assertTrue(request.getProperties().containsKey("k2"));
    Assert.assertTrue(dependency.getProperties().containsKey("k1"));
    Assert.assertTrue(dependency.getProperties().containsKey("k2"));
    Assert.assertEquals("v1", request.getProperties().get("k1"));
    Assert.assertEquals("v2", request.getProperties().get("k2"));
    Assert.assertEquals("v1", dependency.getProperties().get("k1"));
    Assert.assertEquals("v2", dependency.getProperties().get("k2"));

  }

  @Test
  public void setsTracestateOnRequestsAndDependenciesFromCurrentSpanEvenIfOperationIdIsSet()
  {
    RequestTelemetry request = new RequestTelemetry();
    RemoteDependencyTelemetry dependency = new RemoteDependencyTelemetry();
    request.getContext().getOperation().setId("foo");
    dependency.getContext().getOperation().setId("foo");

    SpanContextTelemetryInitializer initializer = new SpanContextTelemetryInitializer();

    Tracer tracer = Tracing.getTracer();
    SpanContext parent = SpanContext.create(
        TraceId.generateRandomId(ThreadLocalRandom.current()),
        SpanId.generateRandomId(ThreadLocalRandom.current()),
        TraceOptions.DEFAULT,
        Tracestate.builder()
            .set("k1", "v1")
            .set("k2", "v2")
            .build());

    Span span = tracer.spanBuilderWithRemoteParent("foo", parent).startSpan();
    try (Scope s = tracer.withSpan(span))
    {
      initializer.initialize(request);
      initializer.initialize(dependency);
    }

    Assert.assertTrue(request.getProperties().containsKey("k1"));
    Assert.assertTrue(request.getProperties().containsKey("k2"));
    Assert.assertTrue(dependency.getProperties().containsKey("k1"));
    Assert.assertTrue(dependency.getProperties().containsKey("k2"));
    Assert.assertEquals("v1", request.getProperties().get("k1"));
    Assert.assertEquals("v2", request.getProperties().get("k2"));
    Assert.assertEquals("v1", dependency.getProperties().get("k1"));
    Assert.assertEquals("v2", dependency.getProperties().get("k2"));;
  }
}