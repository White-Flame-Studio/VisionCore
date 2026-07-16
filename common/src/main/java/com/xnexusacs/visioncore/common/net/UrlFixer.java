package com.xnexusacs.visioncore.common.net;

import java.net.URI;

public interface UrlFixer {

    boolean supports(URI uri);

    URI fix(URI uri);
}
