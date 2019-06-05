package com.microsoft.applicationinsights.opencensus;

import com.microsoft.applicationinsights.common.CommonUtils;
import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import com.microsoft.applicationinsights.extensibility.context.OperationContext;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import io.opencensus.trace.BlankSpan;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracestate.Entry;
import io.opencensus.trace.Tracing;

public class SpanContextTelemetryInitializer implements TelemetryInitializer  {

  private final Tracer tracer = Tracing.getTracer();

  @Override
  public void initialize(Telemetry telemetry) {
    Span currentSpan = tracer.getCurrentSpan();
    if (currentSpan == null || currentSpan == BlankSpan.INSTANCE) {
      return;
    }

    SpanContext currentSpanContext = currentSpan.getContext();
    if (currentSpanContext == null || !currentSpanContext.isValid()) {
      return;
    }

    OperationContext operationContext = telemetry.getContext().getOperation();
    String currentOperationId = operationContext.getId();

    if (CommonUtils.isNullOrEmpty(currentOperationId)) {
      String traceId = currentSpan.getContext().getTraceId().toLowerBase16();
      operationContext.setId(traceId);

      if (CommonUtils.isNullOrEmpty(operationContext.getParentId())) {
        operationContext
            .setParentId(
                "|" + traceId + "." + currentSpanContext.getSpanId().toLowerBase16() + ".");
      }
    }

    if ((telemetry instanceof RemoteDependencyTelemetry || telemetry instanceof RequestTelemetry)) {
      for (Entry e : currentSpanContext.getTracestate().getEntries()) {
        if (!telemetry.getProperties().containsKey(e.getKey())) {
          telemetry.getProperties().put(e.getKey(), e.getValue());
        }
      }
    }
  }
}
