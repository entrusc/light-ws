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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author Florian Frankenberger
 */
public class WebServicePostMapping extends WebServiceMapping {

    private static final String FILE_UPLOAD_CONTENT_TYPE = "application/octet-stream";

    private static final Pattern MULTIPART_BOUNDARY_PATTERN = Pattern.compile("boundary\\=(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPART_CONTENT_DISPOSITION_PATTERN = Pattern.compile("^Content\\-Disposition\\:[\\s]+form-data.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPART_CONTENT_DISPOSITION_FILENAME_PATTERN = Pattern.compile("filename\\=\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPART_CONTENT_DISPOSITION_FIELDNAME_PATTERN = Pattern.compile("name\\=\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPART_CONTENT_TYPE_PATTERN = Pattern.compile("^Content\\-Type\\:[\\s]+(.*)", Pattern.CASE_INSENSITIVE);


    private static final ObjectReader OBJECT_READER = new ObjectMapper().registerModule(new JavaTimeModule()).reader();

    private Class<?> postParamType = null;
    private int postParamPos;

    public WebServicePostMapping(String pathPrefix, Method method) {
        super(HttpMethod.POST, pathPrefix, method);
        initPostParameter();
    }

    private void initPostParameter() {
        Method method = getMethod();
        final Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; ++i) {
            final Parameter param = parameters[i];
            if (param.isAnnotationPresent(PostParameter.class)) {
                postParamPos = i;
                postParamType = param.getType();
                return; //we take the first best post param (there shoud not be more than one!)
            }
        }
        //if no post parameter is declared then we pass the parameter
    }

    @Override
    protected void addParameters(HttpServletRequest request, Object[] parameters) {
        if (this.postParamType != null) {
            final String contentType = request.getContentType().trim().toLowerCase();
            if (contentType.startsWith("application/json")) {
                handleJsonPost(request, parameters);
            } else if (contentType.startsWith("multipart/form-data")) {
                handleMultipartPost(request, parameters);
            } else {
                throw new IllegalArgumentException("Given post parameter was of type \"" + request.getContentType()
                        + "\" and not of type application/json or multipart/form-data");
            }
        }
    }

    private void handleJsonPost(HttpServletRequest request, Object[] parameters) throws IllegalArgumentException {
        try (Reader reader = request.getReader()) {
            Object postParameter = OBJECT_READER.forType(postParamType).readValue(reader);
            parameters[postParamPos] = postParameter;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Given post parameter could "
                    + "not be converted to type " + postParamType, ex);
        }
    }

    private static class Part {
        private String name;
        private int posStart;
        private int length;
        private String fileName;
        private String contentType;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPosStart() {
            return posStart;
        }

        public void setPosStart(int posStart) {
            this.posStart = posStart;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public String toString() {
            return "Part{" + "name=" + name + ", posStart=" + posStart + ", length=" + length + ", fileName=" + fileName + ", contentType=" + contentType + '}';
        }

    }

    private void handleMultipartPost(HttpServletRequest request, Object[] parameters) throws IllegalArgumentException {
        if (postParamType != UploadedFile.class) {
            throw new IllegalStateException("Multipart upload but method does not accept a uploaded file");
        }
        final String contentType = request.getContentType().trim();
        Matcher matcher = MULTIPART_BOUNDARY_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            throw new IllegalArgumentException("multipart not properly structured");
        }
        final String delimeter = "--" + matcher.group(1);

        try {
            // store everything in a file
System.out.println("Uploading ... ");
long time = System.nanoTime();
            final File tmpFullFile = File.createTempFile("uploaded", ".dat");
            try (InputStream in = request.getInputStream()) {
                Files.copy(in, tmpFullFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
System.out.println(String.format("took %.2f ms", (System.nanoTime() - time) / 1_000_000f));

            // first scan of file to find boundaries
System.out.println("Parsing ... ");
time = System.nanoTime();
            int pos = 0;
            Map<String, Part> multipartMap = new LinkedHashMap<>();
            Part currentPart = null;
            try (BufferedReader in = new BufferedReader(new FileReader(tmpFullFile))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith(delimeter)) {
                        if (currentPart != null) {
                            currentPart.setLength(pos - currentPart.getPosStart());
                            if (currentPart.getName() != null
                                    && currentPart.getContentType() != null
                                    && currentPart.getContentType().equals(FILE_UPLOAD_CONTENT_TYPE)) {
                               multipartMap.put(currentPart.getName(), currentPart);
                            }
                        }
                        currentPart = new Part();
                    }

                    if (line.trim().isEmpty() && currentPart != null
                            && currentPart.getPosStart() == 0) {
                        currentPart.setPosStart(pos + line.length() + 2);
                    }

                    if (MULTIPART_CONTENT_DISPOSITION_PATTERN.matcher(line).matches()) {
                        matcher = MULTIPART_CONTENT_DISPOSITION_FILENAME_PATTERN.matcher(line);
                        if (matcher.find()) {
                            if (currentPart != null) {
                                currentPart.setFileName(matcher.group(1));
                            }
                        }
                        matcher = MULTIPART_CONTENT_DISPOSITION_FIELDNAME_PATTERN.matcher(line);
                        if (matcher.find()) {
                            if (currentPart != null) {
                                currentPart.setName(matcher.group(1));
                            }
                        }
                    } else {
                        final Matcher contentTypeMatcher = MULTIPART_CONTENT_TYPE_PATTERN.matcher(line);
                        if (contentTypeMatcher.matches()) {
                            if (currentPart != null) {
                                currentPart.setContentType(contentTypeMatcher.group(1));
                            }
                        }
                    }

                    pos += line.length() + 2; //CR + LF
                }
            }
System.out.println(String.format("took %.2f ms", (System.nanoTime() - time) / 1_000_000f));

            if (multipartMap.isEmpty()) {
                throw new IllegalStateException("no file uploaded");
            }
            if (multipartMap.size() > 1) {
                throw new IllegalStateException("multiple files uploaded");
            }
            System.out.println(multipartMap);

System.out.println("Extracting ... ");
time = System.nanoTime();
            Part filePart = multipartMap.entrySet().iterator().next().getValue();
            final File tmpPartFile = File.createTempFile("part_uploaded", ".dat");
            try (InputStream in = new FileInputStream(tmpFullFile)) {
                try (OutputStream out = new FileOutputStream(tmpPartFile)) {
                    in.skip(filePart.getPosStart());

                    byte[] buffer = new byte[4096];
                    int read = 0;
                    int bytesLeft = filePart.getLength();
                    while (bytesLeft > 0
                            && (read = in.read(buffer, 0, Math.min(bytesLeft, buffer.length))) > -1) {
                        if (read > 0) {
                            out.write(buffer, 0, read);
                            bytesLeft -= read;
                        }
                    }
                }
            }
System.out.println(String.format("took %.2f ms", (System.nanoTime() - time) / 1_000_000f));

            parameters[this.postParamPos] = new UploadedFile(tmpPartFile,
                    filePart.getFileName(), filePart.getContentType());
        } catch (IOException e) {
            throw new IllegalArgumentException("Given post parameter could "
                    + "not be converted to type " + postParamType, e);
        }
    }

}
