package com.github.pdfviewer.source;

import android.content.Context;
import com.github.pdfviewer.shockwave.pdfium.PdfDocument;
import com.github.pdfviewer.shockwave.pdfium.PdfiumCore;
import java.io.IOException;

public interface DocumentSource {
    PdfDocument createDocument(Context context, PdfiumCore core, String password) throws IOException;
}
