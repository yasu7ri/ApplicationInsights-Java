/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.web.extensibility.initializers;

import com.microsoft.applicationinsights.common.CommonUtils;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import com.microsoft.applicationinsights.web.internal.correlation.CorrelationContext;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import java.util.Map;

/**
 * Created by yonisha on 2/16/2015.
 */
public class WebOperationIdTelemetryInitializer extends WebTelemetryInitializerBase {

    /**
     * Initializes the properties of the given telemetry.
     */
    @Override
    protected void onInitializeTelemetry(Telemetry telemetry) {
        Span currentSpan = Tracer.getCurrentSpan();
        if (currentSpan == null){
            return;
        }

        SpanContext currentSpanContext = currentSpan.getContext();

        if (currentSpanContext == null || !currentSpanContext.isValid()) {
            return;
        }

        String traceId = currentSpanContext.getTraceId().toLowerBase16();
        // set operationId to the request telemetry's operation ID
        if (CommonUtils.isNullOrEmpty(telemetry.getContext().getOperation().getId())) {
            telemetry.getContext().getOperation().setId(traceId);
        }

        // set operationId to the request telemetry's operation ID
        if (CommonUtils.isNullOrEmpty(telemetry.getContext().getOperation().getParentId())) {
            telemetry.getContext().getOperation().setParentId("|" + traceId + "." + currentSpanContext.getSpanId().toLowerBase16() + ".");
        }

        CorrelationContext correlationContext = ThreadContext.getRequestTelemetryContext().getCorrelationContext();
        if (correlationContext != null) {
            // add correlation context to properties
            Map<String, String> correlationContextMap = correlationContext.getMappings();
            for (String key : correlationContextMap.keySet()) {
                if (telemetry.getProperties().get(key) == null) {
                    telemetry.getProperties().put(key, correlationContextMap.get(key));
                }
            }
        }
    }
}
