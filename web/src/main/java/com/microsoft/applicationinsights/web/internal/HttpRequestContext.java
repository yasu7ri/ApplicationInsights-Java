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

package com.microsoft.applicationinsights.web.internal;

import com.microsoft.applicationinsights.extensibility.context.SessionContext;
import com.microsoft.applicationinsights.extensibility.context.UserContext;
import com.microsoft.applicationinsights.web.internal.correlation.tracecontext.Tracestate;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;

import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.web.internal.cookies.SessionCookie;
import com.microsoft.applicationinsights.web.internal.cookies.UserCookie;
import com.microsoft.applicationinsights.web.internal.correlation.CorrelationContext;

/**
 * Created by yonisha on 2/2/2015.
 */
public class HttpRequestContext {
//    private RequestTelemetry requestTelemetry;
    //private long requestStartTimeTicks;
    private SessionCookie sessionCookie;
    private UserCookie userCookie;
    private boolean isNewSession = false;
    private HttpServletRequest servletRequest;
    private final CorrelationContext correlationContext;
    //private Tracestate tracestate;
    //private int traceflag;
    //private final AtomicInteger currentChildId = new AtomicInteger();

    public String OperationName; //TODO
    public SessionContext SessionContext; //TODO
    public UserContext UserContext; //TODO
    public String UserAgent; //TODO from span

    public HttpRequestContext() {
        this(null);
    }

/*    public Tracestate getTracestate() {
        return tracestate;
    }

    public void setTracestate(
        Tracestate tracestate) {
        this.tracestate = tracestate;
    }*/

    public HttpRequestContext(HttpServletRequest servletRequest) {
//        requestTelemetry = new RequestTelemetry();
        //requestStartTimeTicks = ticks;
        this.servletRequest = servletRequest;
        correlationContext = new CorrelationContext();
    }

/*    public int getTraceflag() {
        return traceflag;
    }

    public void setTraceflag(int traceflag) {
        this.traceflag = traceflag;
    }*/

    public CorrelationContext getCorrelationContext() {
        return correlationContext;
    }

/*    public RequestTelemetry getHttpRequestTelemetry() {
        return requestTelemetry;
    }

    public long getRequestStartTimeTicks() {
        return requestStartTimeTicks;
    }*/

    public void setSessionCookie(SessionCookie sessionCookie) {
        this.sessionCookie = sessionCookie;
    }

    public SessionCookie getSessionCookie() {
        return sessionCookie;
    }

    public void setUserCookie(UserCookie userCookie) {
        this.userCookie = userCookie;
    }

    public UserCookie getUserCookie() {
        return userCookie;
    }

    public void setIsNewSession(boolean isNewSession) {
        this.isNewSession = isNewSession;
    }

    public boolean getIsNewSession() {
        return isNewSession;
    }

    public HttpServletRequest getHttpServletRequest() {
        return servletRequest;
    }

/*	public int incrementChildId() {
		return this.currentChildId.addAndGet(1);
	}*/
}
