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

package com.microsoft.applicationinsights.agentc.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {

    private static final Logger logger = LoggerFactory.getLogger(Configuration.class);

    static Map<String, Map<String, Object>> getInstrumentationConfig(Properties props) {

        Map<String, Map<String, Object>> instrumentationConfig = new HashMap<>();

        Map<String, Object> servletConfig = new HashMap<>();
        servletConfig.put("captureRequestServerHostname", true);
        servletConfig.put("captureRequestServerPort", true);
        servletConfig.put("captureRequestScheme", true);
        servletConfig.put("captureRequestCookies", Arrays.asList("ai_user", "ai_session"));

        Map<String, Object> jdbcConfig = new HashMap<>();
        jdbcConfig.put("captureBindParametersIncludes", Collections.emptyList());
        jdbcConfig.put("captureResultSetNavigate", false);
        jdbcConfig.put("captureGetConnection", false);

        String explainPlanThresholdInMS = props.getProperty("appInsights.jdbc.explainPlanThresholdInMS");
        if (explainPlanThresholdInMS != null) {
            try {
                jdbcConfig.put("explainPlanThresholdMillis", Long.parseLong(explainPlanThresholdInMS));
            } catch (NumberFormatException e) {
                logger.error("could not parse param explainPlanThresholdInMS: {}", explainPlanThresholdInMS, e);
            }
        }

        instrumentationConfig.put("servlet", servletConfig);
        instrumentationConfig.put("jdbc", jdbcConfig);

        return instrumentationConfig;
    }
}
