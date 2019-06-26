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

package de.darkblue.light.ws;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Use this class as post parameter for a file upload
 */
public class UploadedFile {

    private final File tmpFile;
    private final String fileName;
    private final String contentType;

    UploadedFile(File tmpFile, String fileName, String contentType) {
        this.tmpFile = tmpFile;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public InputStream openInputStream() throws IOException {
        return new FileInputStream(tmpFile);
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * Returns the temporary file where the data
     * is currently stored. Note that the file
     * is marked for auto removal when the VM
     * quits.
     *
     * @return
     */
    public File getTemporaryFile() {
        return tmpFile;
    }

    /**
     * returns the size of the uploaded file
     *
     * @return
     */
    public long getSize() {
        return this.tmpFile.length();
    }

}
