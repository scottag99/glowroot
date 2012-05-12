/**
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.informantproject.plugin.servlet;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.informantproject.api.Message;
import org.informantproject.api.MessageSupplier;
import org.informantproject.api.Metric;
import org.informantproject.api.Optional;
import org.informantproject.api.PluginServices;
import org.informantproject.api.Stopwatch;
import org.informantproject.api.Supplier;
import org.informantproject.api.weaving.Aspect;
import org.informantproject.api.weaving.InjectMethodArg;
import org.informantproject.api.weaving.InjectReturn;
import org.informantproject.api.weaving.InjectTarget;
import org.informantproject.api.weaving.InjectTraveler;
import org.informantproject.api.weaving.IsEnabled;
import org.informantproject.api.weaving.OnAfter;
import org.informantproject.api.weaving.OnBefore;
import org.informantproject.api.weaving.OnReturn;
import org.informantproject.api.weaving.Pointcut;

/**
 * Defines pointcuts and captures data on
 * {@link javax.servlet.http.HttpServlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)}
 * and
 * {@link javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)}
 * calls.
 * 
 * By default only calls to the top-most Filter and to the top-most Servlet are captured.
 * "isWarnOnSpanOutsideTrace" core configuration property can be used to enable capturing of nested
 * Filters and nested Servlets as well.
 * 
 * This plugin is careful not to rely on request or session objects being thread-safe.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
// TODO add support for async servlets (servlet 3.0)
@Aspect
public class ServletAspect {

    private static final String CAPTURE_STARTUP_PROPERTY_NAME = "captureStartup";

    private static final PluginServices pluginServices = PluginServices
            .get("org.informantproject.plugins:servlet-plugin");

    private static final ThreadLocal<ServletMessageSupplier> topLevelServletMessageSupplier =
            new ThreadLocal<ServletMessageSupplier>();

    @Pointcut(typeName = "javax.servlet.Servlet", methodName = "service", methodArgs = {
            "javax.servlet.ServletRequest", "javax.servlet.ServletResponse" },
            metricName = "http request")
    public static class ServletAdvice {
        private static final Metric metric = pluginServices.getMetric(ServletAdvice.class);
        @IsEnabled
        public static boolean isEnabled() {
            // only enabled if it is not contained in another servlet or filter span
            return pluginServices.isEnabled() && topLevelServletMessageSupplier.get() == null;
        }
        @OnBefore
        public static Stopwatch onBefore(@InjectMethodArg Object realRequest) {
            HttpServletRequest request = HttpServletRequest.from(realRequest);
            // request parameter map is collected in afterReturningRequestGetParameterPointcut()
            // session info is collected here if the request already has a session
            ServletMessageSupplier messageSupplier;
            // passing "false" so it won't create a session if the request doesn't already have one
            HttpSession session = request.getSession(false);
            if (session == null) {
                messageSupplier = new ServletMessageSupplier(request.getMethod(),
                        request.getRequestURI(),
                        null, null);
            } else {
                messageSupplier = new ServletMessageSupplier(request.getMethod(),
                        request.getRequestURI(),
                        session.getId(), getSessionAttributes(session));
            }
            topLevelServletMessageSupplier.set(messageSupplier);
            Stopwatch stopwatch = pluginServices.startTrace(messageSupplier, metric);
            if (session != null) {
                Optional<String> sessionUsernameAttributePath = ServletPluginPropertyUtils
                        .getSessionUsernameAttributePath();
                if (sessionUsernameAttributePath.isPresent()) {
                    // capture username now, don't use a lazy supplier
                    Optional<String> username = getSessionAttributeTextValue(session,
                            sessionUsernameAttributePath.get());
                    pluginServices.setUsername(Supplier.of(username));
                }
            }
            return stopwatch;
        }
        @OnAfter
        public static void onAfter(@InjectTraveler Stopwatch stopwatch) {
            stopwatch.stop();
            topLevelServletMessageSupplier.set(null);
        }
    }

    @Pointcut(typeName = "javax.servlet.http.HttpServlet", methodName = "/do.*/", methodArgs = {
            "javax.servlet.http.HttpServletRequest", "javax.servlet.http.HttpServletResponse" },
            metricName = "http request")
    public static class HttpServletAdvice extends ServletAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return ServletAdvice.isEnabled();
        }
        @OnBefore
        public static Stopwatch onBefore(@InjectMethodArg Object realRequest) {
            return ServletAdvice.onBefore(realRequest);
        }
        @OnAfter
        public static void onAfter(@InjectTraveler Stopwatch stopwatch) {
            ServletAdvice.onAfter(stopwatch);
        }
    }

    @Pointcut(typeName = "javax.servlet.Filter", methodName = "doFilter", methodArgs = {
            "javax.servlet.ServletRequest", "javax.servlet.ServletResponse",
            "javax.servlet.FilterChain" }, metricName = "http request")
    public static class FilterAdvice extends ServletAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return ServletAdvice.isEnabled();
        }
        @OnBefore
        public static Stopwatch onBefore(@InjectMethodArg Object realRequest) {
            return ServletAdvice.onBefore(realRequest);
        }
        @OnAfter
        public static void onAfter(@InjectTraveler Stopwatch stopwatch) {
            ServletAdvice.onAfter(stopwatch);
        }
    }

    /*
     * ================== Http Servlet Request Parameters ==================
     */

    private static final ThreadLocal<Boolean> inRequestGetParameterPointcut =
            new BooleanThreadLocal();

    @Pointcut(typeName = "javax.servlet.ServletRequest", methodName = "/getParameter.*/",
            methodArgs = { ".." })
    public static class GetParameterAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return pluginServices.isEnabled();
        }
        @OnAfter
        public static void onAfter(@InjectTarget Object realRequest) {
            if (inRequestGetParameterPointcut.get()) {
                return;
            }
            inRequestGetParameterPointcut.set(true);
            // only now is it safe to get parameters (if parameters are retrieved before this, it
            // could prevent a servlet from choosing to read the underlying stream instead of using
            // the getParameter* methods) see SRV.3.1.1 "When Parameters Are Available"
            try {
                ServletMessageSupplier messageSupplier = topLevelServletMessageSupplier.get();
                if (messageSupplier != null && !messageSupplier.isRequestParameterMapCaptured()) {
                    // this request is being traced and the request parameter map hasn't been
                    // captured yet
                    HttpServletRequest request = HttpServletRequest.from(realRequest);
                    messageSupplier.captureRequestParameterMap(request.getParameterMap());
                }
            } finally {
                // taking no chances on re-setting thread local (thus the second try/finally)
                inRequestGetParameterPointcut.set(false);
            }
        }
    }

    /*
     * ================== Http Session Attributes ==================
     */

    @Pointcut(typeName = "javax.servlet.http.HttpServletRequest", methodName = "getSession",
            methodArgs = { ".." })
    public static class GetSessionAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return pluginServices.isEnabled();
        }
        @OnReturn
        public static void onReturn(@InjectReturn Object realSession) {
            HttpSession session = HttpSession.from(realSession);
            // either getSession(), getSession(true) or getSession(false) has triggered this
            // pointcut
            // after calls to the first two (no-arg, and passing true), a new session may have been
            // created (the third one -- passing false -- could be ignored but is harmless)
            ServletMessageSupplier messageSupplier = topLevelServletMessageSupplier.get();
            if (messageSupplier != null && session != null && session.isNew()) {
                messageSupplier.setSessionIdUpdatedValue(session.getId());
            }
        }
    }

    @Pointcut(typeName = "javax.servlet.http.HttpSession", methodName = "invalidate")
    public static class SessionInvalidateAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return pluginServices.isEnabled();
        }
        @OnBefore
        public static void onBefore(@InjectTarget Object realSession) {
            HttpSession session = HttpSession.from(realSession);
            ServletMessageSupplier messageSupplier = getRootServletMessageSupplier(session);
            if (messageSupplier != null) {
                messageSupplier.setSessionIdUpdatedValue("");
            }
        }
    }

    // TODO support deprecated HttpSession.putValue()

    @Pointcut(typeName = "javax.servlet.http.HttpSession", methodName = "setAttribute",
            methodArgs = { "java.lang.String", "java.lang.Object" })
    public static class SetAttributeAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return pluginServices.isEnabled();
        }
        @OnAfter
        public static void onAfter(@InjectTarget Object realSession, @InjectMethodArg String name,
                @InjectMethodArg Object value) {

            HttpSession session = HttpSession.from(realSession);
            // name is non-null per HttpSession.setAttribute() javadoc, but value may be null
            // (which per the javadoc is the same as calling removeAttribute())
            ServletMessageSupplier messageSupplier = getRootServletMessageSupplier(session);
            if (messageSupplier != null) {
                updateUsernameIfApplicable(name, value, session);
                updateSessionAttributesIfApplicable(messageSupplier, name, value, session);
            }
        }
    }

    @Pointcut(typeName = "javax.servlet.http.HttpSession", methodName = "removeAttribute",
            methodArgs = { "java.lang.String" })
    public static class RemoveAttributeAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return pluginServices.isEnabled();
        }
        @OnAfter
        public static void onAfter(@InjectTarget Object realSession, @InjectMethodArg String name) {
            // calling HttpSession.setAttribute() with null value is the same as calling
            // removeAttribute(), per the setAttribute() javadoc
            SetAttributeAdvice.onAfter(realSession, name, null);
        }
    }

    /*
     * ================== Startup ==================
     */

    @Pointcut(typeName = "javax.servlet.ServletContextListener", methodName = "contextInitialized",
            methodArgs = { "javax.servlet.ServletContextEvent" }, metricName = "servlet startup")
    public static class ContextInitializedAdvice {
        private static final Metric metric = pluginServices.getMetric(
                ContextInitializedAdvice.class);
        @IsEnabled
        public static boolean isEnabled() {
            return pluginServices.isEnabled();
        }
        @OnBefore
        public static Stopwatch onBefore(@InjectTarget Object listener) {
            if (pluginServices.getBooleanProperty(CAPTURE_STARTUP_PROPERTY_NAME)) {
                return pluginServices.startTrace(MessageSupplier.of("servlet context initialized"
                        + " ({{listener}})", listener.getClass().getName()), metric);
            } else {
                return null;
            }
        }
        @OnAfter
        public static void onAfter(@InjectTraveler Stopwatch stopwatch) {
            if (stopwatch != null) {
                stopwatch.stop();
            }
        }
    }

    @Pointcut(typeName = "javax.servlet.Servlet", methodName = "init",
            methodArgs = { "javax.servlet.ServletConfig" }, metricName = "servlet startup")
    public static class ServletInitAdvice {
        private static final Metric metric = pluginServices.getMetric(ServletInitAdvice.class);
        @IsEnabled
        public static boolean isEnabled() {
            return pluginServices.isEnabled();
        }
        @OnBefore
        public static Stopwatch onBefore(@InjectTarget Object servlet) {
            if (pluginServices.getBooleanProperty(CAPTURE_STARTUP_PROPERTY_NAME)) {
                return pluginServices.startTrace(MessageSupplier.of("servlet init ({{filter}})",
                        servlet.getClass().getName()), metric);
            } else {
                return null;
            }
        }
        @OnAfter
        public static void onAfter(@InjectTraveler Stopwatch stopwatch) {
            if (stopwatch != null) {
                stopwatch.stop();
            }
        }
    }

    @Pointcut(typeName = "javax.servlet.Filter", methodName = "init",
            methodArgs = { "javax.servlet.FilterConfig" }, metricName = "servlet startup")
    public static class FilterInitAdvice {
        private static final Metric metric = pluginServices.getMetric(FilterInitAdvice.class);
        @IsEnabled
        public static boolean isEnabled() {
            return pluginServices.isEnabled();
        }
        @OnBefore
        public static Stopwatch onBefore(@InjectTarget Object filter) {
            if (pluginServices.getBooleanProperty(CAPTURE_STARTUP_PROPERTY_NAME)) {
                return pluginServices.startTrace(MessageSupplier.of("filter init ({{filter}})",
                        filter.getClass().getName()), metric);
            } else {
                return null;
            }
        }
        @OnAfter
        public static void onAfter(@InjectTraveler Stopwatch stopwatch) {
            if (stopwatch != null) {
                stopwatch.stop();
            }
        }
    }

    private static void updateUsernameIfApplicable(String name, Object value, HttpSession session) {
        if (value == null) {
            // if username value is set to null, don't clear it
            return;
        }
        Optional<String> sessionUsernameAttributePath = ServletPluginPropertyUtils
                .getSessionUsernameAttributePath();
        if (sessionUsernameAttributePath.isPresent()) {
            // capture username now, don't use a lazy supplier
            if (sessionUsernameAttributePath.get().equals(name)) {
                // it's unlikely, but possible, that toString() returns null
                pluginServices.setUsername(Supplier.of(Optional.fromNullable(value.toString())));
            } else if (sessionUsernameAttributePath.get().startsWith(name + ".")) {
                Optional<String> val = getSessionAttributeTextValue(session,
                        sessionUsernameAttributePath.get());
                if (val.isPresent()) {
                    // if username is absent, don't clear it
                    pluginServices.setUsername(Supplier.of(val));
                }
            }
        }
    }

    private static void updateSessionAttributesIfApplicable(ServletMessageSupplier messageSupplier,
            String name,
            Object value, HttpSession session) {

        if (ServletPluginPropertyUtils.isCaptureAllSessionAttributes()) {
            if (value == null) {
                messageSupplier
                        .putSessionAttributeChangedValue(name, Optional.absent(String.class));
            } else {
                // it's unlikely, but possible, that toString() returns null
                messageSupplier.putSessionAttributeChangedValue(name, Optional.fromNullable(value
                        .toString()));
            }
        } else if (ServletPluginPropertyUtils.getSessionAttributeNames().contains(name)) {
            // update all session attributes (possibly nested) at or under the set attribute
            for (String path : ServletPluginPropertyUtils.getSessionAttributePaths()) {
                if (path.equals(name)) {
                    if (value == null) {
                        messageSupplier.putSessionAttributeChangedValue(path, Optional.absent(
                                String.class));
                    } else {
                        // it's unlikely, but possible, that toString() returns null
                        messageSupplier.putSessionAttributeChangedValue(path,
                                Optional.fromNullable(
                                        value.toString()));
                    }
                } else if (path.startsWith(name + ".")) {
                    if (value == null) {
                        // no need to navigate path since it will always be Optional.absent()
                        messageSupplier.putSessionAttributeChangedValue(path, Optional.absent(
                                String.class));
                    } else {
                        Optional<String> val = getSessionAttributeTextValue(session, path);
                        messageSupplier.putSessionAttributeChangedValue(path, val);
                    }
                }
            }
        }
    }

    private static ServletMessageSupplier getRootServletMessageSupplier(HttpSession session) {
        Supplier<Message> rootMessageSupplier = pluginServices.getRootMessageSupplier();
        if (!(rootMessageSupplier instanceof ServletMessageSupplier)) {
            return null;
        }
        ServletMessageSupplier rootServletMessageSupplier =
                (ServletMessageSupplier) rootMessageSupplier;
        String sessionId;
        if (rootServletMessageSupplier.getSessionIdUpdatedValue() != null) {
            sessionId = rootServletMessageSupplier.getSessionIdUpdatedValue();
        } else {
            sessionId = rootServletMessageSupplier.getSessionIdInitialValue();
        }
        if (!session.getId().equals(sessionId)) {
            // the target session for this pointcut is not the same as the MessageSupplier
            return null;
        }
        return rootServletMessageSupplier;
    }

    private static Map<String, String> getSessionAttributes(HttpSession session) {
        Set<String> sessionAttributePaths = ServletPluginPropertyUtils.getSessionAttributePaths();
        if (sessionAttributePaths == null || sessionAttributePaths.isEmpty()) {
            return null;
        }
        if (ServletPluginPropertyUtils.isCaptureAllSessionAttributes()) {
            // special single value of "*" means dump all http session attributes
            Map<String, String> sessionAttributeMap = new HashMap<String, String>();
            for (Enumeration<?> e = session.getAttributeNames(); e.hasMoreElements();) {
                String attributeName = (String) e.nextElement();
                Object value = session.getAttribute(attributeName);
                // value shouldn't be null, but its (remotely) possible that a concurrent request
                // for the same session just removed the attribute
                String valueString = value == null ? null : value.toString();
                sessionAttributeMap.put(attributeName, valueString);
            }
            return sessionAttributeMap;
        } else {
            Map<String, String> sessionAttributeMap = new HashMap<String, String>(
                    sessionAttributePaths.size());
            // dump only http session attributes in list
            for (String attributePath : sessionAttributePaths) {
                Optional<String> value = getSessionAttributeTextValue(session, attributePath);
                if (value.isPresent()) {
                    sessionAttributeMap.put(attributePath, value.get());
                }
            }
            return sessionAttributeMap;
        }
    }

    private static Optional<String> getSessionAttributeTextValue(HttpSession session,
            String attributePath) {

        if (attributePath.indexOf('.') == -1) {
            // fast path
            Object value = session.getAttribute(attributePath);
            if (value == null) {
                return Optional.absent();
            } else {
                return Optional.of(value.toString());
            }
        } else {
            String[] path = attributePath.split("\\.");
            Object curr = session.getAttribute(path[0]);
            Optional<?> value = PathUtil.getValue(curr, path, 1);
            if (value.isPresent()) {
                return Optional.of(value.get().toString());
            } else {
                return Optional.absent();
            }
        }
    }

    private static class BooleanThreadLocal extends ThreadLocal<Boolean> {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    }
}