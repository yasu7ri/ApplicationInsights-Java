package com.microsoft.applicationinsights.web.internal.correlation;

import io.opencensus.trace.Annotation;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.EndSpanOptions;
import io.opencensus.trace.Link;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.TraceOptions;
import io.opencensus.trace.Tracestate;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


// TODO comment
final class NotExportableSpan extends Span {

  private static final Tracestate TRACESTATE_DEFAULT = Tracestate.builder().build();
  private static final EnumSet<Options> RECORD_EVENTS_SPAN_OPTIONS = EnumSet.of(Options.RECORD_EVENTS);

  public static Span createFromParent(SpanContext parentContext) {
    SpanId spanId = SpanId.generateRandomId(ThreadLocalRandom.current());
    Tracestate tracestate = TRACESTATE_DEFAULT;
    TraceId traceId;
    TraceOptions traceOptions = TraceOptions.DEFAULT;
    if (parentContext != null && parentContext.isValid()) {
      traceId = parentContext.getTraceId();
      tracestate = parentContext.getTracestate();
      traceOptions = parentContext.getTraceOptions();
    } else {
      traceId = TraceId.generateRandomId(ThreadLocalRandom.current());
    }
    return new NotExportableSpan(SpanContext.create(traceId, spanId, traceOptions, tracestate));
  }

  private NotExportableSpan(SpanContext context) {
    super(context, RECORD_EVENTS_SPAN_OPTIONS);
  }

  @Override
  public void addAnnotation(String s, Map<String, AttributeValue> map) {
  }

  @Override
  public void addAnnotation(Annotation annotation) {
  }

  @Override
  public void addLink(Link link) {
  }

  @Override
  public void end(EndSpanOptions endSpanOptions) {
  }
}