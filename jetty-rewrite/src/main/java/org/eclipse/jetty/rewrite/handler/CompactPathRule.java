//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.rewrite.handler;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.URIUtil;

/**
 * Rewrite the URI by compacting to remove //
 */
public class CompactPathRule extends Rule implements Rule.ApplyURI
{
    public CompactPathRule()
    {
        _handling = false;
        _terminating = false;
    }

    @Override
    public void applyURI(Request request, String oldURI, String newURI) throws IOException
    {
        String uri = request.getRequestURI();
        if (uri.startsWith("/"))
            uri = URIUtil.compactPath(uri);
        request.setHttpURI(HttpURI.build(request.getHttpURI(), uri));
    }

    @Override
    public String matchAndApply(String target, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        if (target.startsWith("/"))
            return URIUtil.compactPath(target);
        return target;
    }
}
