package com.github.pdfviewer.source;

import android.content.Context;
import com.github.pdfviewer.shockwave.pdfium.PdfDocument;
import com.github.pdfviewer.shockwave.pdfium.PdfiumCore;
import java.io.IOException;

public class ByteArraySource implements DocumentSource {

    private byte[] data;

    public ByteArraySource(byte[] data) {
        this.data = data;
    }

    @Override
    public PdfDocument createDocument(Context context, PdfiumCore core, String password) throws IOException {
        return core.newDocument(data, password);
    }
}
