package com.microsoft.applicationinsights.web.internal.correlation;

import com.microsoft.applicationinsights.extensibility.context.OperationContext;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.web.internal.correlation.mocks.MockProfileFetcher;
import com.microsoft.applicationinsights.web.utils.ServletUtils;
import io.opencensus.common.Scope;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.TraceOptions;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracestate;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TraceContextCorrelationTests {

    private static MockProfileFetcher mockProfileFetcher;
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern REQUEST_TELEMETRY_ID = Pattern.compile("^\\|[a-f0-9]{32}.[a-f0-9]{16}.$");
    private static final Pattern TRACEPARENT = Pattern.compile("^00-[a-f0-9]{32}-[a-f0-9]{16}-0[0-1]{1}$");
    private static final Tracer TRACER = Tracing.getTracer();

    @Before
    public void testInitialize() {

        // initialize mock profile fetcher (for resolving ikeys to appIds)
        mockProfileFetcher = new MockProfileFetcher();
        InstrumentationKeyResolver.INSTANCE.setProfileFetcher(mockProfileFetcher);
        InstrumentationKeyResolver.INSTANCE.clearCache();
        TraceContextCorrelation.setIsW3CBackCompatEnabled(true);
    }

    @Test
    public void testTraceparentAreResolved() {

        //setup
        Map<String, String> headers = new HashMap<>();
        headers.put(ServletUtils.TRACEPARENT_HEADER_NAME, "00-0123456789abcdef0123456789abcdef-0123456789abcdef-00");

        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        RequestTelemetry requestTelemetry = new RequestTelemetry();

        //run
        Scope scope = TraceContextCorrelation.startRequestScope(request, response, requestTelemetry);
        Assert.assertNotNull(scope);

        //validate we have generated proper ID's
        Assert.assertTrue(REQUEST_TELEMETRY_ID.matcher(requestTelemetry.getId()).matches());
        Assert.assertTrue(requestTelemetry.getId().startsWith("|0123456789abcdef0123456789abcdef."));

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertEquals("0123456789abcdef0123456789abcdef", operation.getId());
        Assert.assertEquals("|0123456789abcdef0123456789abcdef.0123456789abcdef.", operation.getParentId());
    }

    @Test
    public void testCorrelationIdsAreResolvedIfNoTraceparentHeader() {

        //setup - no headers
        Map<String, String> headers = new HashMap<>();
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        RequestTelemetry requestTelemetry = new RequestTelemetry();

        //run
        Scope scope = TraceContextCorrelation.startRequestScope(request, response, requestTelemetry);
        Assert.assertNotNull(scope);
        Assert.assertNotNull(TRACER.getCurrentSpan());

        //validate operation context ID's - there is no parent, so parentId should be null, traceId
        // is newly generated and request.Id is based on new traceId-spanId
        OperationContext operation = requestTelemetry.getContext().getOperation();

        Assert.assertTrue(TRACE_ID_PATTERN.matcher(operation.getId()).matches());
        Assert.assertTrue(REQUEST_TELEMETRY_ID.matcher(requestTelemetry.getId()).matches());

        // First trace will have it's own spanId also.
        Assert.assertTrue(requestTelemetry.getId().startsWith("|" + operation.getId() + "."));
        Assert.assertNull(operation.getParentId());
    }

    @Test
    public void testCorrelationIdsAreResolvedIfTraceparentEmpty() {

        //setup - empty RequestId
        Map<String, String> headers = new HashMap<>();
        headers.put(ServletUtils.TRACEPARENT_HEADER_NAME, "");

        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        RequestTelemetry requestTelemetry = new RequestTelemetry();

        //run
        Scope scope = TraceContextCorrelation.startRequestScope(request, response, requestTelemetry);
        Assert.assertNotNull(scope);
        Assert.assertNotNull(TRACER.getCurrentSpan());

        //validate operation context ID's - there is no parent, so parentId should be null, traceId
        // is newly generated and request.Id is based on new traceId-spanId
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertTrue(TRACE_ID_PATTERN.matcher(operation.getId()).matches());
        Assert.assertTrue(REQUEST_TELEMETRY_ID.matcher(requestTelemetry.getId()).matches());

        // First trace will have it's own spanId also.
        Assert.assertTrue(requestTelemetry.getId().startsWith("|" + operation.getId() + "."));
        Assert.assertNull(operation.getParentId());
    }

    @Test
    public void testCorrelationIdsWithLegacyRequestIdHeader() {

        //setup - no headers
        Map<String, String> headers = new HashMap<>();
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, "|rootId.1.2.3.");

        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        RequestTelemetry requestTelemetry = new RequestTelemetry();

        //run
        Scope scope = TraceContextCorrelation.startRequestScope(request, response, requestTelemetry);
        Assert.assertNotNull(scope);
        Assert.assertNotNull(TRACER.getCurrentSpan());

        //validate operation context ID's - there is no parent, so parentId should be null, traceId
        // is newly generated and request.Id is based on new traceId-spanId
        OperationContext operation = requestTelemetry.getContext().getOperation();

        Assert.assertTrue(TRACE_ID_PATTERN.matcher(operation.getId()).matches());
        Assert.assertTrue(REQUEST_TELEMETRY_ID.matcher(requestTelemetry.getId()).matches());

        // First trace will have it's own spanId also.
        Assert.assertTrue(requestTelemetry.getId().startsWith("|" + operation.getId() + "."));
        Assert.assertEquals("|rootId.1.2.3.", operation.getParentId());
        Assert.assertTrue(requestTelemetry.getProperties().containsKey("ai_legacyRootId"));
        Assert.assertEquals("rootId", requestTelemetry.getProperties().get("ai_legacyRootId"));
    }

    @Test
    public void testCorrelationIdsWithW3CCompatibleRequestIdHeader() {

        //setup - no headers
        Map<String, String> headers = new HashMap<>();
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, "|abcdef0123456789abcdef0123456789.1.2.3.");

        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        RequestTelemetry requestTelemetry = new RequestTelemetry();

        //run
        Scope scope = TraceContextCorrelation.startRequestScope(request, response, requestTelemetry);
        Assert.assertNotNull(scope);
        Assert.assertNotNull(TRACER.getCurrentSpan());

        //validate operation context ID's - there is no parent, so parentId should be null, traceId
        // is newly generated and request.Id is based on new traceId-spanId
        OperationContext operation = requestTelemetry.getContext().getOperation();

        Assert.assertEquals("abcdef0123456789abcdef0123456789", operation.getId());
        Assert.assertTrue(REQUEST_TELEMETRY_ID.matcher(requestTelemetry.getId()).matches());

        // First trace will have it's own spanId also.
        Assert.assertTrue(requestTelemetry.getId().startsWith("|" + operation.getId() + "."));
        Assert.assertEquals("|abcdef0123456789abcdef0123456789.1.2.3.", operation.getParentId());
        Assert.assertFalse(requestTelemetry.getProperties().containsKey("ai_legacyRootId"));
    }

    @Test
    public void testLegacyRequestIdIsIgnoredInPresenceOfTraceparent() {

        //setup
        Map<String, String> headers = new HashMap<>();
        headers.put(ServletUtils.TRACEPARENT_HEADER_NAME, "00-0123456789abcdef0123456789abcdef-0123456789abcdef-00");
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, "|rootId.1.2.3");

        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        RequestTelemetry requestTelemetry = new RequestTelemetry();

        //run
        Scope scope = TraceContextCorrelation.startRequestScope(request, response, requestTelemetry);
        Assert.assertNotNull(scope);
        Assert.assertNotNull(TRACER.getCurrentSpan());

        //validate we have generated proper ID's
        Assert.assertTrue(REQUEST_TELEMETRY_ID.matcher(requestTelemetry.getId()).matches());
        Assert.assertTrue(requestTelemetry.getId().startsWith("|0123456789abcdef0123456789abcdef."));

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertEquals("0123456789abcdef0123456789abcdef", operation.getId());
        Assert.assertEquals("|0123456789abcdef0123456789abcdef.0123456789abcdef.", operation.getParentId());
        Assert.assertFalse(requestTelemetry.getProperties().containsKey("ai_legacyRootId"));
    }

    @Test
    public void testLegacyRequestIdIsIgnoredWhenBackCompatIsOff() {

        //setup
        TraceContextCorrelation.setIsW3CBackCompatEnabled(false);

        Map<String, String> headers = new HashMap<>();
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, "|rootId.1.2.3");

        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        RequestTelemetry requestTelemetry = new RequestTelemetry();

        //run
        Scope scope = TraceContextCorrelation.startRequestScope(request, response, requestTelemetry);
        Assert.assertNotNull(scope);
        Assert.assertNotNull(TRACER.getCurrentSpan());

        //validate we have generated proper ID's
        Assert.assertTrue(REQUEST_TELEMETRY_ID.matcher(requestTelemetry.getId()).matches());

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertTrue(TRACE_ID_PATTERN.matcher(operation.getId()).matches());
        Assert.assertNull(operation.getParentId());
        Assert.assertFalse(requestTelemetry.getProperties().containsKey("ai_legacyRootId"));
    }

    @Test
    public void Agent_DependencyTraceparentGenerationWithoutParentContext() {
        String traceparent = TraceContextCorrelation.generateChildDependencyTraceparent();
        Assert.assertTrue(TRACEPARENT.matcher(traceparent).matches());
        Assert.assertTrue(traceparent.endsWith("00"));
   }

    @Test
    public void Agent_DependencyTraceparentGenerationWithParentContextSampledIn() {
        try (Scope _ = TRACER.spanBuilder("test")
                .setSampler(Samplers.alwaysSample())
                .startScopedSpan()) {
            String traceparent = TraceContextCorrelation.generateChildDependencyTraceparent();

            Assert.assertTrue(TRACEPARENT.matcher(traceparent).matches());

            String traceId = TRACER.getCurrentSpan().getContext().getTraceId().toLowerBase16();
            Assert.assertTrue(traceparent.startsWith("00-" + traceId + "-"));
            Assert.assertTrue(traceparent.endsWith("01"));
        }
    }

    @Test
    public void Agent_DependencyTraceparentGenerationWithParentContextSampledOut() {
        try (Scope _ = TRACER.spanBuilder("test")
                .setSampler(Samplers.neverSample())
                .startScopedSpan()) {
            String traceparent = TraceContextCorrelation.generateChildDependencyTraceparent();

            Assert.assertTrue(TRACEPARENT.matcher(traceparent).matches());

            String traceId = TRACER.getCurrentSpan().getContext().getTraceId().toLowerBase16();
            Assert.assertTrue(traceparent.startsWith("00-" + traceId + "-"));
            Assert.assertTrue(traceparent.endsWith("00"));
        }
    }

    @Test
    public void Agent_DependencyTargetNoContext() {

        String targetNull = TraceContextCorrelation.generateChildDependencyTarget(null);
        Assert.assertEquals("", targetNull);

        String targetEmpty = TraceContextCorrelation.generateChildDependencyTarget("");
        Assert.assertEquals("", targetEmpty);
    }

    @Test
    public void Agent_DependencyTargetInvalidContext() {

        String target = TraceContextCorrelation.generateChildDependencyTarget("k equals v");
        Assert.assertEquals("", target);
    }

    @Test
    public void Agent_DependencyTargetNoAppId() {

        String target = TraceContextCorrelation.generateChildDependencyTarget("k=v");
        Assert.assertEquals("", target);
    }

    @Test
    public void Agent_DependencyTargetAppId() {

        String target = TraceContextCorrelation.generateChildDependencyTarget("appId=cid-v1:xyz");
        Assert.assertNotNull(target);
        Assert.assertEquals("cid-v1:xyz", target);
    }

    @Test
    public void Agent_DependencyTargetAppIdSameAsMyApp() {

        String target = TraceContextCorrelation.generateChildDependencyTarget("appId=cid-v1:defaultId");
        Assert.assertEquals("", target);
    }

    @Test
    public void Agent_DependencyTargetMultiAppId() {

        String target = TraceContextCorrelation.generateChildDependencyTarget("appId=cid-v1:a,appId=cid-v1:a");
        Assert.assertEquals("", target);
    }

    @Test
    public void Agent_DependencyTracestateNoParentContext() {

        String tracestate = TraceContextCorrelation.retriveTracestate();
        Assert.assertNull(tracestate);
    }

    @Test
    public void Agent_DependencyTracestateWithParentContextNoTracestate() {
        try (Scope _ = TRACER.spanBuilder("test")
            .setSampler(Samplers.alwaysSample())
            .startScopedSpan()) {
            String tracestate = TraceContextCorrelation.retriveTracestate();
            Assert.assertNull(tracestate);
        }
    }

    @Test
    public void Agent_DependencyTracestateWithParentContextAndTracestate() {

        Tracestate tracestate = Tracestate.builder()
            .set("k1", "v1")
            .set("k2", "v2")
            .build();
        SpanContext parent = SpanContext.create(
            TraceId.generateRandomId(ThreadLocalRandom.current()),
            SpanId.generateRandomId(ThreadLocalRandom.current()),
            TraceOptions.DEFAULT,
            tracestate);

        try (Scope _ = TRACER.spanBuilderWithRemoteParent("test", parent)
            .setSampler(Samplers.alwaysSample())
            .startScopedSpan()) {
            String tracestateString = TraceContextCorrelation.retriveTracestate();
            Assert.assertEquals("k2=v2,k1=v1", tracestateString);
        }
    }

    @Test
    public void Agent_DependencyRequestIdFromTraceparent()
    {
        String requestId = TraceContextCorrelation.createChildIdFromTraceparentString("00-0123456789abcdef0123456789abcdef-0123456789abcdef-00");
        Assert.assertEquals("|0123456789abcdef0123456789abcdef.0123456789abcdef.", requestId);
    }

    @Test
    public void Agent_DependencyRequestIdFromTraceparentInvalid()
    {
        String requestId = TraceContextCorrelation.createChildIdFromTraceparentString("invalid traceparent");
        Assert.assertEquals("", requestId);
    }

}
