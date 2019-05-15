package com.microsoft.applicationinsights.opencensus;

import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import com.sun.deploy.trace.Trace;
import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import org.junit.Assert;
import org.junit.Test;

public final class OpenCensusTelemetryInitializerTests {

  @Test
  public void noopWithoutCurrentSpan()
  {
    TraceTelemetry trace = new TraceTelemetry();
    OpenCensusTelemetryInitializer initializer = new OpenCensusTelemetryInitializer();
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

    OpenCensusTelemetryInitializer initializer = new OpenCensusTelemetryInitializer();
    initializer.initialize(trace);
    Assert.assertEquals("foo", trace.getContext().getOperation().getId());
    Assert.assertEquals("bar", trace.getContext().getOperation().getParentId());
  }

  @Test
  public void setsContextFromCurrentSpan()
  {
    TraceTelemetry trace = new TraceTelemetry();
    OpenCensusTelemetryInitializer initializer = new OpenCensusTelemetryInitializer();

    Tracer tracer = Tracing.getTracer();
    Span span = tracer.spanBuilder("foo").startSpan();
    try (Scope _ = tracer.withSpan(span))
    {
      initializer.initialize(trace);
    }

    String expectedTraceId = span.getContext().getTraceId().toLowerBase16();
    String expectedSpanId = span.getContext().getSpanId().toLowerBase16();
    Assert.assertEquals(expectedTraceId, trace.getContext().getOperation().getId());
    Assert.assertEquals("|" + expectedTraceId + "." + expectedSpanId + ".",
        trace.getContext().getOperation().getParentId());
  }
}