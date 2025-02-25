//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.server.handler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.jetty.http.DateGenerator;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.io.IOResources;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Default Handler.
 *
 * This handle will deal with unhandled requests in the server.
 * For requests for favicon.ico, the Jetty icon is served.
 * For requests to '/' a 404 with a list of known contexts is served.
 * For all other requests a normal 404 is served.
 */
public class DefaultHandler extends Handler.Abstract
{
    private static final Logger LOG = LoggerFactory.getLogger(DefaultHandler.class);

    private final long _faviconModifiedMs = (System.currentTimeMillis() / 1000) * 1000L;
    private final HttpField _faviconModified = new PreEncodedHttpField(HttpHeader.LAST_MODIFIED, DateGenerator.formatDate(_faviconModifiedMs));
    private ByteBuffer _favicon;
    private boolean _serveFavIcon = true;
    private boolean _showContexts = true;

    public DefaultHandler()
    {
        this(true, true);
    }

    public DefaultHandler(@Name("serveFavIcon") boolean serveFavIcon, @Name("showContexts")boolean showContexts)
    {
        super(InvocationType.NON_BLOCKING);
        _serveFavIcon = serveFavIcon;
        _showContexts = showContexts;
    }

    @Override
    public void setServer(Server server)
    {
        super.setServer(server);
        if (server != null)
            initFavIcon();
    }

    private void initFavIcon()
    {
        if (_favicon != null)
            return;

        Server server = Objects.requireNonNull(getServer());

        byte[] favbytes = null;
        try
        {
            Resource faviconRes = server.getDefaultFavicon();
            if (faviconRes != null)
            {
                try (InputStream is = IOResources.asInputStream(faviconRes))
                {
                    favbytes = IO.readBytes(is);
                }
            }
        }
        catch (Exception e)
        {
            LOG.warn("Unable to find default favicon", e);
        }
        finally
        {
            _favicon = BufferUtil.toBuffer(favbytes);
        }
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        String method = request.getMethod();

        // little cheat for common request
        if (isServeFavIcon() && _favicon != null && HttpMethod.GET.is(method) && Request.getPathInContext(request).equals("/favicon.ico"))
        {
            ByteBuffer content = BufferUtil.EMPTY_BUFFER;
            if (_faviconModifiedMs > 0 && request.getHeaders().getDateField(HttpHeader.IF_MODIFIED_SINCE) == _faviconModifiedMs)
                response.setStatus(HttpStatus.NOT_MODIFIED_304);
            else
            {
                response.setStatus(HttpStatus.OK_200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "image/x-icon");
                response.getHeaders().put(HttpHeader.CONTENT_LENGTH, _favicon.remaining());
                response.getHeaders().add(_faviconModified);
                response.getHeaders().put(HttpHeader.CACHE_CONTROL.toString(), "max-age=360000,public");
                content = _favicon.slice();
            }
            response.write(true, content, callback);
            return true;
        }

        if (!isShowContexts() || !HttpMethod.GET.is(method) || !Request.getPathInContext(request).equals("/"))
        {
            Response.writeError(request, response, callback, HttpStatus.NOT_FOUND_404, null);
            return true;
        }

        response.setStatus(HttpStatus.NOT_FOUND_404);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, MimeTypes.Type.TEXT_HTML_UTF_8.toString());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(outputStream, UTF_8))
        {
            writer.append("<!DOCTYPE html>\n");
            writer.append("<html lang=\"en\">\n<head>\n");
            writer.append("<title>Error 404 - Not Found</title>\n");
            writer.append("<meta charset=\"utf-8\">\n");
            writer.append("<style>body { font-family: sans-serif; } table, td { border: 1px solid #333; } td, th { padding: 5px; } thead, tfoot { background-color: #333; color: #fff; } </style>\n");
            writer.append("</head>\n<body>\n");
            writer.append("<h2>Error 404 - Not Found.</h2>\n");
            writer.append("<p>No context on this server matched or handled this request.</p>\n");
            writer.append("<p>Contexts known to this server are:</p>\n");

            writer.append("<table class=\"contexts\"><thead><tr>");
            writer.append("<th>Context Path</th>");
            writer.append("<th>Display Name</th>");
            writer.append("<th>Status</th>");
            writer.append("<th>LifeCycle</th>");
            writer.append("</tr></thead><tbody>\n");

            Server server = getServer();
            List<ContextHandler> handlers = server == null ? Collections.emptyList() : server.getDescendants(ContextHandler.class);

            for (ContextHandler context : handlers)
            {
                writer.append("<tr><td>");

                String contextPath = context.getContextPath();
                String href = URIUtil.encodePath(contextPath);
                if (contextPath.length() > 1 && !contextPath.endsWith("/"))
                {
                    href += '/';
                }

                if (context.isRunning())
                {
                    writer.append("<a href=\"").append(href).append("\">");
                }
                writer.append(StringUtil.replace(contextPath, "%", "&#37;"));
                if (context.isRunning())
                {
                    writer.append("</a>");
                }
                writer.append("</td><td>");
                // Display Name

                if (StringUtil.isNotBlank(context.getDisplayName()))
                {
                    writer.append(StringUtil.sanitizeXmlString(context.getDisplayName()));
                }
                writer.append("&nbsp;</td><td>");
                // Available

                if (context.isAvailable())
                {
                    writer.append("Available");
                }
                else
                {
                    writer.append("<em>Not</em> Available");
                }
                writer.append("</td><td>");
                // State
                writer.append(context.getState());
                writer.append("</td></tr>\n");
            }

            writer.append("</tbody></table><hr/>\n");
            writer.append("<a href=\"https://jetty.org\"><img alt=\"icon\" src=\"/favicon.ico\"/></a>&nbsp;");
            writer.append("<a href=\"https://jetty.org\">Powered by Eclipse Jetty:// Server</a><hr/>\n");
            writer.append("</body>\n</html>\n");
            writer.flush();
            ByteBuffer content = BufferUtil.toBuffer(outputStream.toByteArray());
            response.getHeaders().put(HttpHeader.CONTENT_LENGTH, content.remaining());
            response.write(true, content, callback);
            return true;
        }
    }

    /**
     * @return Returns true if the handle can server the jetty favicon.ico
     */
    @ManagedAttribute("True if the favicon.ico is served")
    public boolean isServeFavIcon()
    {
        return _serveFavIcon;
    }

    /**
     * Set true if the handle can server the jetty favicon.ico.
     * @param serveFavIcon true if the handle can server the jetty favicon.ico
     */
    public void setServeFavIcon(boolean serveFavIcon)
    {
        _serveFavIcon = serveFavIcon;
    }

    @ManagedAttribute("True if the contexts should be shown in the default 404 page")
    public boolean isShowContexts()
    {
        return _showContexts;
    }

    public void setShowContexts(boolean show)
    {
        _showContexts = show;
    }

    @Override
    public String toString()
    {
        String name = TypeUtil.toShortName(getClass());
        return String.format("%s@%x{showContext=%b,favIcon=%b,%s}", name, hashCode(), _showContexts, _serveFavIcon, getState());
    }
}
