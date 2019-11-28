/*
 * The MIT License
 *
 * Copyright 2019 Florian Frankenberger.
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

import javax.servlet.http.HttpServletResponse;

/**
 * A custom response of type T
 */
public class Response<T> {

    private final T responseObject;
    private int statusCode = HttpServletResponse.SC_OK;

    public Response() {
        this(null);
    }

    public Response(T returnValue) {
        this(returnValue, HttpServletResponse.SC_OK);
    }

    public Response(T returnValue, int statusCode) {
        this.responseObject = returnValue;
        this.setStatusCode(statusCode);
    }

    public T getResponseObject() {
        return responseObject;
    }

    public final void setStatusCode(int statusCode) {
        if (statusCode < 100 || statusCode >= 1000) {
            throw new IllegalArgumentException("Status code needs to be between 100 and 999");
        }
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }



}
