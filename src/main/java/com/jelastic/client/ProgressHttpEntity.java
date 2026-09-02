package com.jelastic.client;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Wraps an entity to report the upload progress.
 *
 * <p>Replaces the former {@code CustomMultiPartEntity} which extended the deprecated {@code MultipartEntity}: the
 * multipart body is now built by {@code MultipartEntityBuilder} and simply wrapped here.</p>
 */
public class ProgressHttpEntity extends HttpEntityWrapper {

    private final ProgressListener listener;

    public ProgressHttpEntity(HttpEntity wrapped, ProgressListener listener) {
        super(wrapped);
        this.listener = listener;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        super.writeTo(new CountingOutputStream(outstream, listener));
    }

    public interface ProgressListener {
        void transferred(long transferred);
    }

    static class CountingOutputStream extends FilterOutputStream {

        private final ProgressListener listener;
        private long transferred;

        CountingOutputStream(OutputStream out, ProgressListener listener) {
            super(out);
            this.listener = listener;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            transferred += len;
            listener.transferred(transferred);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            transferred++;
            listener.transferred(transferred);
        }
    }
}
