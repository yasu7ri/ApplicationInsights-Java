package com.microsoft.applicationinsights.opencensus;

import com.microsoft.applicationinsights.common.CommonUtils;
import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import com.microsoft.applicationinsights.extensibility.context.OperationContext;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

public class OpenCensusTelemetryInitializer implements TelemetryInitializer  {

  private final Tracer tracer = Tracing.getTracer();

  @Override
  public void initialize(Telemetry telemetry) {

    OperationContext operationContext = telemetry.getContext().getOperation();
    String currentOperationId = operationContext.getId();

    if (!CommonUtils.isNullOrEmpty(currentOperationId)) {
      // already initialized
      return;
    }

    Span currentSpan = tracer.getCurrentSpan();
    if (currentSpan == null) {
      return;
    }

    SpanContext currentSpanContext = currentSpan.getContext();
    if (currentSpanContext == null || !currentSpanContext.isValid()) {
      return;
    }

    String traceId = currentSpan.getContext().getTraceId().toLowerBase16();
    operationContext.setId(traceId);

    if (CommonUtils.isNullOrEmpty(operationContext.getParentId())) {
      operationContext
          .setParentId("|" + traceId + "." + currentSpanContext.getSpanId().toLowerBase16() + ".");
    }
  }
}
