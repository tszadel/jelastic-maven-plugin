package com.jelastic.client;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;

/**
 * Keeps the boundary, and only the boundary, in the {@code Content-Type} of a multipart body.
 *
 * <p>A multipart entity built with a charset advertises it as an extra parameter, <b>after</b> the boundary:</p>
 *
 * <pre>multipart/form-data; boundary=BTDqPv9a1viO6ve; charset=UTF-8</pre>
 *
 * <p>A parser that takes everything after {@code boundary=} then looks for a separator that was never written, and
 * fails on the request instead of reading it. The Jelastic uploader is one of those; see
 * {@code JelasticMojo.uploadBody} for what it answers.</p>
 *
 * <p>The charset still governs how the part headers are encoded — it is only kept out of the header, exactly as
 * every browser does.</p>
 */
public class BoundaryOnlyContentType extends HttpEntityWrapper {

    public BoundaryOnlyContentType(HttpEntity wrapped) {
        super(wrapped);
    }

    @Override
    public Header getContentType() {
        Header original = super.getContentType();
        if (original == null || original.getValue() == null) {
            return original;
        }

        ContentType parsed;
        try {
            parsed = ContentType.parse(original.getValue());
        } catch (RuntimeException unparseable) {
            // Nothing to strip from a value we cannot read: send it untouched rather than replace it with a guess.
            return original;
        }

        String boundary = parsed.getParameter("boundary");
        if (boundary == null) {
            return original;
        }

        return new BasicHeader(HTTP.CONTENT_TYPE, parsed.getMimeType() + "; boundary=" + boundary);
    }
}
