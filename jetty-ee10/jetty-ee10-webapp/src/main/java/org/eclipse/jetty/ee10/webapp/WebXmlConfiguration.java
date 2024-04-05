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

package org.eclipse.jetty.ee10.webapp;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.eclipse.jetty.ee10.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configure by parsing default web.xml and web.xml
 */
public class WebXmlConfiguration extends AbstractConfiguration
{
    private static final Logger LOG = LoggerFactory.getLogger(WebXmlConfiguration.class);

    public WebXmlConfiguration()
    {
        super(new Builder().addDependencies(WebInfConfiguration.class));
    }

    /**
     *
     */
    @Override
    public void preConfigure(WebAppContext context) throws Exception
    {
        //parse webdefault-ee10.xml
        String defaultsDescriptor = context.getDefaultsDescriptor();
        if (StringUtil.isNotBlank(defaultsDescriptor))
        {
            Resource dftResource = context.getResourceFactory().newClassLoaderResource(defaultsDescriptor);
            if (Resources.missing(dftResource))
            {
                String pkg = WebXmlConfiguration.class.getPackageName().replace(".", "/") + "/";
                if (defaultsDescriptor.startsWith(pkg))
                {
                    URL url = WebXmlConfiguration.class.getResource(defaultsDescriptor.substring(pkg.length()));
                    if (url != null)
                    {
                        URI uri = url.toURI();
                        dftResource = context.getResourceFactory().newResource(uri);
                    }
                }
                if (Resources.missing(dftResource))
                    dftResource = context.newResource(defaultsDescriptor);
            }
            if (Resources.isReadableFile(dftResource))
                context.getMetaData().setDefaultsDescriptor(new DefaultsDescriptor(dftResource));
        }

        //parse, but don't process web.xml
        Resource webxml = findWebXml(context);
        if (webxml != null)
        {
            context.getMetaData().setWebDescriptor(new WebDescriptor(webxml));
            context.getContext().getServletContext().setEffectiveMajorVersion(context.getMetaData().getWebDescriptor().getMajorVersion());
            context.getContext().getServletContext().setEffectiveMinorVersion(context.getMetaData().getWebDescriptor().getMinorVersion());
        }

        //parse but don't process override-web.xml
        for (String overrideDescriptor : context.getOverrideDescriptors())
        {
            if (StringUtil.isNotBlank(overrideDescriptor))
            {
                Resource orideResource = context.getResourceFactory().newClassLoaderResource(overrideDescriptor);
                if (Resources.missing(orideResource))
                    orideResource = context.newResource(overrideDescriptor);
                if (Resources.isReadableFile(orideResource))
                    context.getMetaData().addOverrideDescriptor(new OverrideDescriptor(orideResource));
            }
        }
    }

    /**
     * Process web-default.xml, web.xml, override-web.xml
     */
    @Override
    public void configure(WebAppContext context) throws Exception
    {
        context.getMetaData().addDescriptorProcessor(new StandardDescriptorProcessor());
    }

    protected Resource findWebXml(WebAppContext context) throws IOException, MalformedURLException
    {
        String descriptor = context.getDescriptor();
        if (descriptor != null)
        {
            Resource web = context.newResource(descriptor);
            if (web != null && !web.isDirectory())
                return web;
        }

        Resource webInf = context.getWebInf();
        if (webInf != null && webInf.isDirectory())
        {
            // do web.xml file
            Resource web = webInf.resolve("web.xml");
            if (Resources.isReadableFile(web))
                return web;
            if (LOG.isDebugEnabled())
                LOG.debug("No WEB-INF/web.xml in {}. Serving files and default/dynamic servlets only", context.getWar());
        }
        return null;
    }

    @Override
    public void deconfigure(WebAppContext context) throws Exception
    {
        context.setWelcomeFiles(null);

        //TODO: ErrorPageErrorHandler is not an ErrorHandler
        if (context.getErrorHandler() instanceof ErrorPageErrorHandler errorPageErrorHandler)
            errorPageErrorHandler.setErrorPages(null);

        // TODO remove classpaths from classloader
    }
}
