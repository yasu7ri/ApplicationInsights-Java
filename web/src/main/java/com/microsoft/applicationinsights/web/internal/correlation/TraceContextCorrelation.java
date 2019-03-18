package com.microsoft.applicationinsights.web.internal.correlation;

import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * A class that is responsible for performing correlation based on W3C protocol.
 * This is a clean implementation of W3C protocol and doesn't have the backward
 * compatibility with AI-RequestId protocol.
 *
 * @author Dhaval Doshi
 */
public class TraceContextCorrelation {

    public static final String TRACEPARENT_HEADER_NAME = "traceparent";
    public static final String TRACESTATE_HEADER_NAME = "tracestate";
    public static final String REQUEST_CONTEXT_HEADER_NAME = "Request-Context";
    public static final String AZURE_TRACEPARENT_COMPONENT_INITIAL = "az";
    public static final String REQUEST_CONTEXT_HEADER_APPID_KEY = "appId";

    private static final Tracer TRACER = Tracing.getTracer();
    private static final Random RANDOM = ThreadLocalRandom.current();
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
     * This method is responsible to perform correlation for incoming request by populating it's
     * traceId, spanId and parentId. It also stores incoming tracestate into ThreadLocal for downstream
     * propagation.
     * @param request
     * @param response
     * @param span
     */
    public static void legacyCorrelation(HttpServletRequest request, HttpServletResponse response,
        Span span, String instrumentationKey) {

        try {
            if (request == null) {
                InternalLogger.INSTANCE.error("Failed to resolve correlation. request is null.");
                return;
            }

            if (response == null) {
                InternalLogger.INSTANCE.error("Failed to resolve correlation. response is null.");
                return;
            }

            if (span == null) {
                InternalLogger.INSTANCE.error("Failed to resolve correlation. requestTelemetry is null.");
                return;
            }

            // Let the callee know the caller's AppId
            addTargetAppIdInResponseHeaderViaRequestContext(response);

            String requestId = request.getHeader(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME);

            if (requestId != null && !requestId.isEmpty()) {
                String legacyOperationId = TelemetryCorrelationUtils.extractRootId(requestId);
                if (!legacyOperationId.equals(span.getContext().getTraceId().toLowerBase16()))
                {
                    span.putAttribute("ai_legacyRootId", AttributeValue.stringAttributeValue(legacyOperationId));
                }
            }

            resolveRequestSource(request, span,instrumentationKey);
        } catch (java.lang.Exception e) {
            InternalLogger.INSTANCE.error("unable to perform correlation :%s", ExceptionUtils.
                getStackTrace(e));
        }
    }


    /**
     * This adds the Request-Context in response header so that the Callee can know what is the caller's AppId.
     * @param response HttpResponse object
     */
    private static void addTargetAppIdInResponseHeaderViaRequestContext(HttpServletResponse response) {

        if (response.containsHeader(REQUEST_CONTEXT_HEADER_NAME)) {
            return;
        }

        String appId = getAppIdWithKey();
        if (appId.isEmpty()) {
            return;
        }

        // W3C protocol doesn't define any behavior for response headers.
        // This is purely AI concept and hence we use RequestContextHeader here.
        response.addHeader(REQUEST_CONTEXT_HEADER_NAME,appId);
    }

    /**
     * Gets AppId prefixed with key to append to Request-Context header
     * @return
     */
    private static String getAppIdWithKey() {
        return REQUEST_CONTEXT_HEADER_APPID_KEY + "=" + getAppId();
    }

    /**
     * Retrieves the appId for the current active config's instrumentation key.
     */
    public static String getAppId() {

        String instrumentationKey = TelemetryConfiguration.getActive().getInstrumentationKey();
        String appId = InstrumentationKeyResolver.INSTANCE.resolveInstrumentationKey(instrumentationKey);

        //it's possible the appId returned is null (e.g. async task is still pending or has failed). In this case, just
        //return and let the next request resolve the ikey.
        if (appId == null) {
            InternalLogger.INSTANCE.trace("Application correlation Id could not be retrieved (e.g. task may be pending or failed)");
            return "";
        }

        return appId;
    }

    /**
     * Resolves the source of a request based on request header information and the appId of the current
     * component, which is retrieved via a query to the AppInsights service.
     * @param request The servlet request.
     * @param span The request telemetry in which source will be populated.
     * @param instrumentationKey The instrumentation key for the current component.
     */
    public static void resolveRequestSource(HttpServletRequest request, Span span, String instrumentationKey) {

        try {

            if (request == null) {
                InternalLogger.INSTANCE.error("Failed to resolve correlation. request is null.");
                return;
            }

            if (instrumentationKey == null || instrumentationKey.isEmpty()) {
                InternalLogger.INSTANCE.error("Failed to resolve correlation. InstrumentationKey is null or empty.");
                return;
            }

            if (span == null) {
                InternalLogger.INSTANCE.error("Failed to resolve correlation. requestTelemetry is null.");
                return;
            }

            if (request.getHeader(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME) != null) {
                    InternalLogger.INSTANCE.trace("Tracestate absent, In backward compatibility mode, will try to resolve "
                        + "request-context");
                    TelemetryCorrelationUtils.resolveRequestSource(request, span, instrumentationKey);
                    return;
            }
            InternalLogger.INSTANCE.info("Skip resolving request source as the following header was not found: %s",
                TRACESTATE_HEADER_NAME);
            return;
        }
        catch(Exception ex) {
            InternalLogger.INSTANCE.error("Failed to resolve request source. Exception information: %s",
                ExceptionUtils.getStackTrace(ex));
        }
    }


    /**
     * Generates the target appId to add to Outbound call
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
        assert keyValue.length == 2;

        String headerAppID = null;
        if (keyValue[0].equals(REQUEST_CONTEXT_HEADER_APPID_KEY)) {
            headerAppID = keyValue[1];
        }

        String currAppId = InstrumentationKeyResolver.INSTANCE.resolveInstrumentationKey(TelemetryConfiguration.getActive()
        .getInstrumentationKey());

        String target = resolve(headerAppID, currAppId);
        if (target == null) {
            InternalLogger.INSTANCE.warn("Target value is null and hence returning empty string");
            return ""; // we want an empty string instead of null so it plays nicer with bytecode injection
        }
        return target;
    }

    /**
     * Extracts the appId/roleName out of Tracestate and compares it with the current appId. It then
     * generates the appropriate source or target.
     */
    private static String generateSourceTargetCorrelation(String instrumentationKey, String appId) {

        assert instrumentationKey != null;
        assert appId != null;

        String myAppId = InstrumentationKeyResolver.INSTANCE.resolveInstrumentationKey(instrumentationKey);

        return resolve(appId, myAppId);
    }

    /**
     * Resolves appId based on appId passed in header and current appId
     * @param headerAppId
     * @param currentAppId
     * @return
     */
    private static String resolve(String headerAppId, String currentAppId) {

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

    /**
     * Helper method to retrieve Tracestate from ThreadLocal
     * @return
     */
    public static String retriveTracestate() {
        Span span = TRACER.getCurrentSpan();
        if (span != null)
        {
            return span.getContext().getTracestate().toString();
        }

        return null;
    }

    /**
     * Generates child TraceParent by retrieving values from ThreadLocal.
     * @return Outbound Traceparent
     */
    public static String generateChildDependencyTraceparent() {
        try {
            Span span = TRACER.getCurrentSpan();
            String traceId = null;
            String spanId = SpanId.generateRandomId(RANDOM).toLowerBase16();
            String flags = "00";
            if (span == null)
            {
                traceId = TraceId.generateRandomId(RANDOM).toLowerBase16();
            }
            else
            {
                flags = span.getContext().getTraceOptions().toLowerBase16();
            }

            return "00" + "-" + traceId + "-" + spanId  + "-" + flags;
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
        assert traceparentArr.length == 4;

        return "|" + traceparentArr[1] + "." + traceparentArr[2] + ".";
    }

    public static void setIsW3CBackCompatEnabled(boolean isW3CBackCompatEnabled) {
        TraceContextCorrelation.isW3CBackCompatEnabled = isW3CBackCompatEnabled;
        InternalLogger.INSTANCE.trace(String.format("W3C Backport mode enabled on Incoming side %s",
            isW3CBackCompatEnabled));
    }
}
