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
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author Florian Frankenberger
 */
public class WebServiceTest {

    private RestTemplate restTemplate;

    public WebServiceTest() {
    }

    @Before
    public void setUpRestTemplate() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse chr) throws IOException {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse chr) throws IOException {
                // do nothing
            }
        });
    }

    private Server setUpWebServer(Object webService) throws Exception {
        Server server = new Server(0); // 0 = any port
        server.setHandler(new HandlerList(
                new WebServiceHandler(webService)
        ));

        server.start();

        return server;
    }

    @Test
    public void testPostMapping() throws Exception {
        Server server = setUpWebServer(new PostWebService());

        InfoObject infoObject  = new InfoObject();
        infoObject.setInfo("some info");
        final ResultObject response =
                restTemplate.postForObject(server.getURI().resolve("/web/say/foobar/12345"), infoObject, ResultObject.class);
        assertEquals("some info for foobar and number 12345", response.getMsg());

        server.stop();
    }

    @Test
    public void testSimpleGetMapping() throws Exception {
        Server server = setUpWebServer(new GetWebService());

        ResponseEntity<ResultObject> response =
                restTemplate.getForEntity(server.getURI().resolve("/web/test/world"), ResultObject.class);
        assertEquals("Hello world", response.getBody().getMsg());
        assertEquals(200, response.getStatusCodeValue());

        server.stop();
    }

    @Test
    public void testAdvancedGetMapping() throws Exception {
        Server server = setUpWebServer(new GetWebService());

        ResponseEntity<ResultObject> response =
                restTemplate.getForEntity(server.getURI().resolve("/web/test2"), ResultObject.class);
        assertEquals("Hello there", response.getBody().getMsg());
        assertEquals(500, response.getStatusCodeValue());

        server.stop();
    }

    @Test
    public void testGetNullMapping() throws Exception {
        Server server = setUpWebServer(new GetWebService());

        ResponseEntity<String> response =
                restTemplate.getForEntity(server.getURI().resolve("/web/test3"), String.class);
        assertEquals("{}", response.getBody());
        assertEquals(404, response.getStatusCodeValue());

        server.stop();
    }

}
