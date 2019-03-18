package com.microsoft.applicationinsights.web.internal.httputils;

import com.microsoft.applicationinsights.web.internal.HttpRequestContext;
import io.opencensus.trace.Span;
import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.Validate;
import org.apache.http.annotation.Experimental;

/**
 * This class implements {@link AsyncListener} to handle span completion for async request handling.
 */
@Experimental
public final class AIHttpServletListener implements AsyncListener {

    /**
     * Instance of {@link HttpRequestContext}
     */
    private final Span span;

    /**
     * Instance of {@link HttpServerHandler}
     */
    private final HttpServerHandler<HttpServletRequest, HttpServletResponse> handler;

    public AIHttpServletListener(HttpServerHandler<HttpServletRequest, HttpServletResponse> handler,
        Span span) {
        Validate.notNull(handler, "HttpServerHandler");
        this.handler = handler;
        this.span = span;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        ServletRequest request = event.getSuppliedRequest();
        ServletResponse response = event.getSuppliedResponse();
        if (request instanceof HttpServletRequest
            && response instanceof HttpServletResponse) {
            handler.handleEnd((HttpServletRequest) request, (HttpServletResponse) response,null,  span);
        }
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        ServletRequest request = event.getSuppliedRequest();
        ServletResponse response = event.getSuppliedResponse();
        if (request instanceof HttpServletRequest
            && response instanceof HttpServletResponse) {
            handler.handleEnd((HttpServletRequest) request, (HttpServletResponse) response, null, span); //TODO timeout exception?
        }
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        ServletRequest request = event.getSuppliedRequest();

        ServletResponse response = event.getSuppliedResponse();
        if (request instanceof HttpServletRequest
            && response instanceof HttpServletResponse) {

            Throwable throwable = null;
            try {
                throwable = event.getThrowable();
            } finally{
                handler.handleEnd((HttpServletRequest) request, (HttpServletResponse) response, throwable, span);
            }
        }
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
        AsyncContext asyncContext = event.getAsyncContext();
        if (asyncContext != null) {
            asyncContext.addListener(this, event.getSuppliedRequest(), event.getSuppliedResponse());
        }
    }
}
