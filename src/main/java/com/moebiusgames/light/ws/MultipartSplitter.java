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

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Splits a multipart body into multipart sections
 *
 * @author Florian Frankenberger
 */
public interface MultipartSplitter {

    /**
     * splits a multipart body according to the boundary.This can be done
     * in java on most systems, but with systems where only an interpreted
     * VM is available you might want to do this using JNA or similar
     *
     * @param boundary the boundary to split the body at (the boundary also
     *                 already contains the leading two dashes)
     * @param body
     * @return
     * @throws java.io.IOException
     */
    List<MultipartSection> split(String boundary, File body) throws IOException;

    public static class MultipartSection {
        private int start;
        private int end;

        public int getEnd() {
            return end;
        }

        public void setEnd(int end) {
            this.end = end;
        }

        public int getStart() {
            return start;
        }

        public void setStart(int start) {
            this.start = start;
        }

        @Override
        public String toString() {
            return "Section{" + "start=" + start + ", end=" + end + '}';
        }
    }
}
