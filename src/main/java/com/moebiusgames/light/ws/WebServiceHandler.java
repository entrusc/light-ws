/*
 * The MIT License
 *
 * Copyright 2018 Florian Frankenberger.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.moebiusgames.light.ws;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * A handler for a jetty web server to handle classes annotated
 * as WebService.
 *
 * @author Florian Frankenberger
 */
public class WebServiceHandler<T> extends AbstractHandler {

    private static final Logger LOGGER = Logger.getLogger(WebServiceHandler.class.getCanonicalName());

    public final T service;
    private final String pathPrefix;

    private final List<WebServiceMapping> methodMappings = new ArrayList<>();

    public WebServiceHandler(T service) {
        this.service = service;
        final Class<? extends Object> serviceClass = service.getClass();

        if (serviceClass.isAnnotationPresent(WebService.class)) {
            pathPrefix = serviceClass.getAnnotation(WebService.class).value();

            for (Method method : serviceClass.getMethods()) {
                if (method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)) {
                    WebServiceMapping mapping = prepareMapping(pathPrefix, method);
                    if (mapping != null) {
                        LOGGER.log(Level.INFO, "Registering [{0}] for {1}.{2}()",
                                new Object[]{mapping.getPath(), serviceClass.getSimpleName(), method.getName()});
                        methodMappings.add(mapping);
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("no webservice descriptor present at class "
                    + serviceClass.getCanonicalName());
        }
    }

    @Override
    public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {

        //only check mapping if path suffix matches
        if (target.startsWith(pathPrefix)) {
            for (WebServiceMapping mapping : this.methodMappings) {
                if (mapping.execute(service, target, request, response)) {
                    baseRequest.setHandled(true);
                    return;
                }
            }
        }
    }

    private static WebServiceMapping prepareMapping(String pathSuffix, Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) {
            return new WebServiceGetMapping(pathSuffix, method);
        } else
            if (method.isAnnotationPresent(PostMapping.class)) {
                return new WebServicePostMapping(pathSuffix, method);
            }
        return null;
    }


}
