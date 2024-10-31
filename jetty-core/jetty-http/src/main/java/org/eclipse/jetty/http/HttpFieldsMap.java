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

package org.eclipse.jetty.http;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jetty.util.StringUtil;

class HttpFieldsMap extends AbstractMap<String, List<String>>
{
    private final HttpFields.Mutable httpFields;

    public HttpFieldsMap(HttpFields.Mutable httpFields)
    {
        this.httpFields = httpFields;
    }

    @Override
    public List<String> get(Object key)
    {
        if (key instanceof String s)
            return httpFields.getValuesList(s);
        return null;
    }

    @Override
    public List<String> put(String key, List<String> value)
    {
        List<String> oldValue = get(key);
        httpFields.put(key, value);
        return oldValue;
    }

    @Override
    public List<String> remove(Object key)
    {
        if (key instanceof String s)
        {
            List<String> oldValue = get(s);
            httpFields.remove(s);
            return oldValue;
        }
        return null;
    }

    @Override
    public Set<Entry<String, List<String>>> entrySet()
    {
        return new AbstractSet<>()
        {
            @Override
            public Iterator<Entry<String, List<String>>> iterator()
            {
                return new Iterator<>()
                {
                    private final Iterator<String> iterator = httpFields.getFieldNamesCollection().iterator();
                    private String name = null;

                    @Override
                    public boolean hasNext()
                    {
                        return iterator.hasNext();
                    }

                    @Override
                    public Entry<String, List<String>> next()
                    {
                        name = iterator.next();
                        return new HttpFieldsEntry(name);
                    }

                    @Override
                    public void remove()
                    {
                        if (name != null)
                        {
                            HttpFieldsMap.this.remove(name);
                            name = null;
                        }
                    }
                };
            }

            @Override
            public int size()
            {
                return httpFields.getFieldNamesCollection().size();
            }
        };
    }

    private class HttpFieldsEntry implements Entry<String, List<String>>
    {
        private final String _name;

        public HttpFieldsEntry(String name)
        {
            _name = name;
        }

        @Override
        public String getKey()
        {
            return _name;
        }

        @Override
        public List<String> getValue()
        {
            return HttpFieldsMap.this.get(_name);
        }

        @Override
        public List<String> setValue(List<String> value)
        {
            return HttpFieldsMap.this.put(_name, value);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o instanceof HttpFieldsEntry other)
                return StringUtil.asciiEqualsIgnoreCase(_name, other.getKey());
            return false;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(StringUtil.asciiToLowerCase(_name));
        }
    }
}
