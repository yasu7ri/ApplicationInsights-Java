package com.microsoft.applicationinsights.web.internal.correlation;

import static com.microsoft.applicationinsights.web.internal.correlation.TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_APPID_KEY;

import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import io.opencensus.common.Scope;
import io.opencensus.trace.BlankSpan;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.TraceOptions;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracestate;
import io.opencensus.trace.Tracestate.Entry;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.propagation.TextFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * A class that is responsible for performing correlation based on W3C protocol.
 * This is a clean implementation of W3C protocol and doesn't have the backward
 * compatibility with AI-RequestId protocol.
 *
 * @author Liudmila Molkova
 */
public class TraceContextCorrelation {
    private final static Tracer TRACER = Tracing.getTracer();
    private final static TextFormat TEXT_FORMAT = Tracing.getPropagationComponent().getTraceContextFormat();
    private final static Tracestate TRACESTATE_DEFAULT = Tracestate.builder().build();

    private final static TextFormat.Getter<HttpServletRequest> contextGetter = new TextFormat.Getter<HttpServletRequest>() {
        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier.getHeader(key);
        }
    };

    /**
      * Switch to enable W3C Backward compatibility with Legacy AI Correlation.
      * By default this is turned ON.
      */
    private static boolean isW3CBackCompatEnabled = true;

    /**
     * Private constructor as we don't expect to create an object of this class.
     */
    private TraceContextCorrelation() {}

    /**
     * This method is responsible to start a new span with context inherited from incoming request
     * trace context and populate the context on RequestTelemetry.
     * This method implements W3C trace-context specification and optionally enables backward
     * compatibility with legacy Request-Id.
     *
     * @param request
     * @param response
     * @param requestTelemetry
     */
    public static Scope startRequestScope(HttpServletRequest request, HttpServletResponse response,
        RequestTelemetry requestTelemetry) {

        Scope requestScope = null;
        try {
            if (request == null) {
                InternalLogger.INSTANCE.error("Failed to resolve correlation. request is null.");
                return null;
            }

            if (response == null) {
                InternalLogger.INSTANCE.error("Failed to resolve correlation. response is null.");
                return null;
            }

            if (requestTelemetry == null) {
                InternalLogger.INSTANCE
                    .error("Failed to resolve correlation. requestTelemetry is null.");
                return null;
            }

            SpanContext parentContext = null;
            try {
                parentContext = TEXT_FORMAT.extract(request, contextGetter);
            }
            catch (Exception e) {
                // TODO (OC) submit bug to OC: there should not really be an exception for missing ctx
                // Currently we cannot distinguish between context parse error and missing context.
                // Logging would be annoying so we just ignore this error and do not even log a message.
            }

            if (parentContext == null && isW3CBackCompatEnabled) {
                // try to parse legacy correlation (Request-Id) as there was no W3C context
                // AND back-compat mode is enabled
                parentContext = tryLegacyCorrelation(request, requestTelemetry);
            }

            // We track request telemetry already - we do not really need Span for it
            // however we need all child telemetry (including telemetry that is reported purely with
            // OpenCensus to correlate with incoming request (it's Span)
            // so we are starting span that we are not going to export through OpenCensus -
            // it is just a wrapper over the SpanContext.
            Span requestSpan = NotExportableSpan.createFromParent(parentContext);

            // put the Span on the ThreadLocal context, we'll need to close scope after we are done
            requestScope = TRACER.withSpan(requestSpan);

            SpanContext childContext = requestSpan.getContext();
            if (childContext.isValid()) {
                String traceId = childContext.getTraceId().toLowerBase16();

                requestTelemetry.setId("|" + traceId + "." + childContext.getSpanId().toLowerBase16() + ".");
                requestTelemetry.getContext().getOperation().setId(traceId);

                // assign parent id
                if (parentContext != null && parentContext.isValid() && requestTelemetry.getContext().getOperation().getParentId() == null) {
                    requestTelemetry.getContext().getOperation()
                        .setParentId("|" + traceId + "." +
                            parentContext.getSpanId().toLowerBase16() + ".");
                }
            }

            // Let the callee know the caller's AppId
            TelemetryCorrelationUtils.addTargetAppIdForResponseHeader(response);
        } catch (java.lang.Exception e) {
            InternalLogger.INSTANCE.error("unable to perform correlation :%s", ExceptionUtils.
                getStackTrace(e));
        }

        return requestScope;
    }

    /**
     * This method processes the legacy Request-ID header for backward compatibility.
     * @param request
     * @return
     */
    private static SpanContext tryLegacyCorrelation(HttpServletRequest request, RequestTelemetry requestTelemetry) {
        String requestId = request.getHeader(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME);

        try {
            if (requestId != null && !requestId.isEmpty()) {
                requestTelemetry.getContext().getOperation().setParentId(requestId);

                String legacyOperationId = TelemetryCorrelationUtils.extractRootId(requestId);

                if (isValidTraceId(legacyOperationId)) {
                    return SpanContext.create(
                        TraceId.fromLowerBase16(legacyOperationId),
                        SpanId.generateRandomId(ThreadLocalRandom.current()),
                        TraceOptions.DEFAULT,
                        TRACESTATE_DEFAULT);
                } else {
                    requestTelemetry.getContext().getProperties().putIfAbsent("ai_legacyRootId", legacyOperationId);
                }
            }
        } catch (Exception e) {
            InternalLogger.INSTANCE.error(String.format("unable to create traceparent from legacy request-id header"
                + " %s", ExceptionUtils.getStackTrace(e)));
        }

        return null;
    }

    public static void setIsW3CBackCompatEnabled(boolean isW3CBackCompatEnabled) {
        TraceContextCorrelation.isW3CBackCompatEnabled = isW3CBackCompatEnabled;
        InternalLogger.INSTANCE.trace(
            String.format("W3C Backport mode enabled on Incoming side %s", isW3CBackCompatEnabled));
    }

    private static boolean isValidTraceId(String legacyRootId) {
        if (legacyRootId == null || legacyRootId.length() != 32) {
            return false;
        }
        for (int i = 0; i < 32; i++) {
            char c = legacyRootId.charAt(i);
            if ('0' <= c && c <= '9') {
                continue;
            }
            if ('a' <= c && c <= 'f') {
                continue;
            }
            return false;
        }
        return true;
    }

    // region dependencies  TODO: we should refactor this for the new agent
    // instrumentation should be done using OpenCensus spans and trace-context propagation implementation


    /**
     * Generates the target appId to add to Outbound call. This method is used in the agent
     * @param requestContext
     * @return
     */
    public static String generateChildDependencyTarget(String requestContext) {
        if (requestContext == null || requestContext.isEmpty()) {
            InternalLogger.INSTANCE.trace("generateChildDependencyTarget: won't continue as requestContext is null or empty.");
            return "";
        }

        String instrumentationKey = TelemetryConfiguration.getActive().getInstrumentationKey();
        if (instrumentationKey == null || instrumentationKey.isEmpty()) {
            InternalLogger.INSTANCE.error("Failed to generate target correlation. InstrumentationKey is null or empty.");
            return "";
        }

        // In W3C we only pass requestContext for the response. So it's expected to have only single key-value pair
        String[] keyValue = requestContext.split("=");
        if (keyValue.length != 2) {
            InternalLogger.INSTANCE.error("generateChildDependencyTarget: invalid context");
            return "";
        }

        String headerAppID = null;
        if (keyValue[0].equals(REQUEST_CONTEXT_HEADER_APPID_KEY)) {
            headerAppID = keyValue[1];
        }

        String currAppId = InstrumentationKeyResolver.INSTANCE.resolveInstrumentationKey(TelemetryConfiguration.getActive()
            .getInstrumentationKey());

        String target = getTargetAppIdIfDifferent(headerAppID, currAppId);
        if (target == null) {
            InternalLogger.INSTANCE.warn("Target value is null and hence returning empty string");
            return ""; // we want an empty string instead of null so it plays nicer with bytecode injection
        }
        return target;
    }

    /**
     * Helper method to retrieve Tracestate from ThreadLocal.  This method is used in the agent
     * @return
     */
    public static String retriveTracestate() {
        try {
            Span currentSpan = TRACER.getCurrentSpan();
            if (currentSpan != null && currentSpan != BlankSpan.INSTANCE && currentSpan.getContext().isValid()) {
                return tracestateToString(currentSpan.getContext().getTracestate());
            }
        }
        catch (Exception ex) {
            InternalLogger.INSTANCE.error("Failed to retrieve tracestate. Exception information: %s", ex.toString());
            InternalLogger.INSTANCE.trace("Stack trace generated is %s", ExceptionUtils.getStackTrace(ex));
        }

        return null;
    }

    /**
     * Generates child TraceParent by retrieving values from ThreadLocal.
     * @return Outbound Traceparent
     */
    public static String generateChildDependencyTraceparent() {
        try {

            TraceId traceId;
            SpanId spanId = SpanId.generateRandomId(ThreadLocalRandom.current());
            TraceOptions options = TraceOptions.DEFAULT;

            Span currentSpan = TRACER.getCurrentSpan();
            if (currentSpan == null || currentSpan == BlankSpan.INSTANCE || !currentSpan.getContext().isValid()) {
                // dependency happens in async call and context is lost
                // or there was no parent request
                traceId = TraceId.generateRandomId(ThreadLocalRandom.current());
            } else {
                traceId = currentSpan.getContext().getTraceId();
                options = currentSpan.getContext().getTraceOptions();
            }

            return new StringBuilder(55)
                .append("00-")
                .append(traceId.toLowerBase16())
                .append("-")
                .append(spanId.toLowerBase16())
                .append("-")
                .append(options.toLowerBase16())
                .toString();
        }
        catch (Exception ex) {
            InternalLogger.INSTANCE.error("Failed to generate child ID. Exception information: %s", ex.toString());
            InternalLogger.INSTANCE.trace("Stack trace generated is %s", ExceptionUtils.getStackTrace(ex));
        }

        return null;
    }

    /**
     * This is helper method to convert traceparent (W3C) format to AI legacy format for supportability
     * @param traceparent
     * @return legacy format traceparent
     */
    public static String createChildIdFromTraceparentString(String traceparent) {
        assert traceparent != null;

        String[] traceparentArr = traceparent.split("-");
        if(traceparentArr.length != 4)
        {
            return "";
        }

        return "|" + traceparentArr[1] + "." + traceparentArr[2] + ".";
    }


    private static String tracestateToString(Tracestate tracestate) {
        List<Entry> entries = tracestate.getEntries();
        if (entries.isEmpty()) {
            return null;
        }

        StringBuilder stringBuilder = new StringBuilder(512);
        for (Tracestate.Entry entry : entries) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(',');
            }
            stringBuilder
                .append(entry.getKey())
                .append('=')
                .append(entry.getValue());
        }

        return stringBuilder.toString();
    }

    /**
     * Returns target appId if it is different than current appId
     * @param headerAppId
     * @param currentAppId
     * @return
     */
    private static String getTargetAppIdIfDifferent(String headerAppId, String currentAppId) {

        //it's possible the appId returned is null (e.g. async task is still pending or has failed). In this case, just
        //return and let the next request resolve the ikey.
        if (currentAppId == null) {
            InternalLogger.INSTANCE.trace("Could not generate source/target correlation as the appId could not be resolved (e.g. task may be pending or failed)");
            return null;
        }

        // if the current appId and the incoming appId are send null
        String result = null;
        if (headerAppId != null && !headerAppId.equals(currentAppId)) {
            result = headerAppId;
        }

        return result;
    }
}
