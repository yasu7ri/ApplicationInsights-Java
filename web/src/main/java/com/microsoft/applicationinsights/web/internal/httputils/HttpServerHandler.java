package com.microsoft.applicationinsights.web.internal.httputils;

import static com.google.common.base.Preconditions.checkNotNull;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.common.CommonUtils;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.util.ThreadLocalCleaner;
import com.microsoft.applicationinsights.web.internal.HttpRequestContext;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import com.microsoft.applicationinsights.web.internal.WebModulesContainer;
import io.opencensus.contrib.http.util.HttpTraceAttributeConstants;
import io.opencensus.contrib.http.util.HttpTraceUtil;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Span.Kind;
import io.opencensus.trace.Span.Options;
import io.opencensus.trace.SpanBuilder;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.propagation.SpanContextParseException;
import io.opencensus.trace.propagation.TextFormat;
import java.net.MalformedURLException;
import java.util.List;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.annotation.Experimental;

/**
 * This Helper Handler class provides the required methods to instrument requests.
 * @param <P> The HttpRequest entity
 * @param <Q> The HttpResponse entity
 */
@Experimental
public final class HttpServerHandler<P /* >>> extends @NonNull Object */, Q> {

    /**
     * Extractor to extract data from request and response
     */
    private final HttpExtractor<P, Q> extractor;

    /**
     * Container that holds collection of
     * {@link com.microsoft.applicationinsights.web.extensibility.modules.WebTelemetryModule}
     */
    private final WebModulesContainer<P, Q> webModulesContainer;

    /**
     * An instance of {@link TelemetryClient} responsible to track exceptions
     */
    private final TelemetryClient telemetryClient;

    /**
     * ThreadLocal Cleaners for Agent connector
     */
    private final List<ThreadLocalCleaner> cleaners;

    private final TextFormat.Getter<P> getter;
    private final TextFormat textFormat;
    private final Tracer tracer;

    /**
     * Creates a new instance of {@link HttpServerHandler}
     *
     * @param extractor The {@code HttpExtractor} used to extract information from request and repsonse
     * @param webModulesContainer The {@code WebModulesContainer} used to hold
     *        {@link com.microsoft.applicationinsights.web.extensibility.modules.WebTelemetryModule}
     */
    public HttpServerHandler(
        Tracer tracer,
        HttpExtractor<P, Q> extractor,
        TextFormat textFormat,
        TextFormat.Getter<P> getter,
        WebModulesContainer<P, Q> webModulesContainer,
        List<ThreadLocalCleaner> cleaners,
        TelemetryClient telemetryClient) {

        Validate.notNull(extractor, "extractor");
        Validate.notNull(webModulesContainer, "WebModuleContainer");
        Validate.notNull(cleaners, "ThreadLocalCleaners");
        this.extractor = extractor;
        this.webModulesContainer = webModulesContainer;
        this.cleaners = cleaners;
        this.tracer = tracer;
        this.textFormat = textFormat;
        this.getter = getter;
        this.telemetryClient = telemetryClient;
    }

    /**
     * This method is used to instrument incoming request and initiate correlation with help of
     * {@link com.microsoft.applicationinsights.web.extensibility.modules.WebRequestTrackingTelemetryModule#onBeginRequest(HttpServletRequest, HttpServletResponse)}
     * @param request incoming Request
     * @param response Response object
     * @return {@link Span} that contains correlation information and metadata about request
     * @throws MalformedURLException
     */
    public Span handleStart(P request, Q response) throws MalformedURLException {
        checkNotNull(request, "request");
        checkNotNull(response, "response");
        SpanBuilder spanBuilder = null;
        String spanName = getSpanName(request);
        // de-serialize the context
        SpanContext spanContext = null;
        try {
            spanContext = textFormat.extract(request, getter);
        } catch (SpanContextParseException e) {
            // TODO: Currently we cannot distinguish between context parse error and missing context.
            // Logging would be annoying so we just ignore this error and do not even log a message.
        }
        if (spanContext == null) {
            spanBuilder = tracer.spanBuilder(spanName).setSpanKind(Kind.SERVER);
        } else {
            spanBuilder = tracer.spanBuilderWithRemoteParent(spanName, spanContext).setSpanKind(Kind.SERVER);
        }

        Span span = spanBuilder.startSpan();
        tracer.withSpan(span);

        HttpRequestContext httpContext = new HttpRequestContext();
        if (span.getOptions().contains(Options.RECORD_EVENTS)) {

            // TODO: ai legacy ids

            String method = extractor.getMethod(request);
            String uriWithoutSessionId = extractor.getUri(request);
            String scheme = extractor.getScheme(request);
            String host = extractor.getHost(request);
            String query = extractor.getQuery(request);
            String url = null;

            putAttributeIfNotEmptyOrNull(
                span, HttpTraceAttributeConstants.HTTP_USER_AGENT, extractor.getUserAgent(request));
            putAttributeIfNotEmptyOrNull(
                span, HttpTraceAttributeConstants.HTTP_HOST, extractor.getHost(request));
            putAttributeIfNotEmptyOrNull(
                span, HttpTraceAttributeConstants.HTTP_METHOD, method);
            putAttributeIfNotEmptyOrNull(
                span, HttpTraceAttributeConstants.HTTP_PATH, extractor.getPath(request));
            putAttributeIfNotEmptyOrNull(
                span, HttpTraceAttributeConstants.HTTP_ROUTE, "");

            if (!CommonUtils.isNullOrEmpty(query)) {
                url = scheme + "://" + host + uriWithoutSessionId + "?" + query;
            } else {
                url = scheme + "://" + host + uriWithoutSessionId;
            }
            putAttributeIfNotEmptyOrNull(
                span, HttpTraceAttributeConstants.HTTP_URL, url);

            httpContext.OperationName =  method + " " + uriWithoutSessionId;

        }

        webModulesContainer.setRequestTelemetryContext(httpContext);
        webModulesContainer.invokeOnBeginRequest(request, response);

        return span;
    }

    /**
     * This method is used to indicate request end instrumentation, complete correlation and record timing, response.
     * Context object is needed as a parameter because in Async requests, handleEnd() can be called
     * on separate thread then where handleStart() was called.
     * @param request HttpRequest object
     * @param response HttpResponse object
     * @param span Span object
     */
    public void handleEnd(P request, Q response, Throwable error,
                          Span span) {

        checkNotNull(span, "span");
        int statusCode = extractor.getStatusCode(response);
        if (span.getOptions().contains(Options.RECORD_EVENTS)) {
            span.putAttribute(
                HttpTraceAttributeConstants.HTTP_STATUS_CODE,
                AttributeValue.longAttributeValue(statusCode));
        }
        handleException(error);
        span.setStatus(HttpTraceUtil.parseResponseStatus(statusCode, error));
        span.end();
        webModulesContainer.invokeOnEndRequest(request, response);
        cleanup();
    }

    /**
     * This method is used to capture runtime exceptions while processing request
     * @param e Exception occurred
     */
    public void handleException(Throwable e) {

        // TODO: it does not correlate
        try {
            Exception ex = (Exception)e;
            InternalLogger.INSTANCE.trace("Unhandled exception while processing request: %s",
                ExceptionUtils.getStackTrace(e));
            if (telemetryClient != null) {
                telemetryClient.trackException(ex);
            }
        } catch (Exception ex) {
            // swallow AI Exception
        }
    }

    /**
     * Remove data from Threadlocal and ThreadLocalCleaners
     */
    private void cleanup() {
        try {
            for (ThreadLocalCleaner cleaner : cleaners) {
                cleaner.clean();
            }
            // clean context after cleaners are run, in-case cleaners need the context object
            ThreadContext.remove();
        } catch (Exception t) {
            InternalLogger.INSTANCE.warn(String.format("unable to perform TLS Cleaning: %s",
                ExceptionUtils.getStackTrace(t)));
        }
    }

    final String getSpanName(P request) {
        // default span name
        String path = this.extractor.getPath(request);
        if (path == null) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    final void addSpanRequestAttributes(Span span, P request) {

    }


    private static void putAttributeIfNotEmptyOrNull(Span span, String key, @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            span.putAttribute(key, AttributeValue.stringAttributeValue(value));
        }
    }
}
