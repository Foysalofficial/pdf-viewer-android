package com.github.pdfviewer.link;

import com.github.pdfviewer.model.LinkTapEvent;

public interface LinkHandler {

    void handleLinkEvent(LinkTapEvent event);
}
