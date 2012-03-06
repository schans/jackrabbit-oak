/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.mk.model;

import org.apache.jackrabbit.mk.store.Binding;
import org.apache.jackrabbit.mk.util.AbstractFilteringIterator;
import org.apache.jackrabbit.mk.util.EmptyIterator;
import org.apache.jackrabbit.mk.util.RangeIterator;
import org.apache.jackrabbit.mk.util.StringUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 */
public class ChildNodeEntriesMap implements ChildNodeEntries {

    protected static final Iterator<ChildNodeEntry> EMPTY_ITER = new EmptyIterator<ChildNodeEntry>();
    
    protected HashMap<String, ChildNodeEntry> entries;

    protected boolean insertionOrdered;
    
    ChildNodeEntriesMap(boolean insertionOrdered) {
        this.insertionOrdered = insertionOrdered;
        if (insertionOrdered) {
            entries = new LinkedHashMap<String, ChildNodeEntry>();
        } else {
            entries = new HashMap<String, ChildNodeEntry>();
        }
    }

    ChildNodeEntriesMap(ChildNodeEntriesMap other) {
        insertionOrdered = other.insertionOrdered;
        if (insertionOrdered) {
            entries = (LinkedHashMap<String, ChildNodeEntry>) other.entries.clone();
        } else {
            entries = (HashMap<String, ChildNodeEntry>) other.entries.clone();
        }
    }

    //------------------------------------------------------------< overrides >

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ChildNodeEntriesMap) {
            return entries.equals(((ChildNodeEntriesMap) obj).entries);
        }
        return false;
    }

    @Override
    public Object clone()  {
        ChildNodeEntriesMap clone = null;
        try {
            clone = (ChildNodeEntriesMap) super.clone();
        } catch (CloneNotSupportedException e) {
            // can't possibly get here
        }
        if (insertionOrdered) {
            clone.entries = (LinkedHashMap<String, ChildNodeEntry>) entries.clone();
        } else {
            clone.entries = (HashMap<String, ChildNodeEntry>) entries.clone();
        }
        return clone;
    }

    @Override
    public boolean inlined() {
        return true;
    }

    //-------------------------------------------------------------< read ops >

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public ChildNodeEntry get(String name) {
        return entries.get(name);
    }

    @Override
    public Iterator<String> getNames(int offset, int count) {
        if (offset < 0 || count < -1) {
            throw new IllegalArgumentException();
        }

        if (offset == 0 && count == -1) {
            return entries.keySet().iterator();
        } else {
            if (offset >= entries.size() || count == 0) {
                return new EmptyIterator<String>();
            }
            if (count == -1 || (offset + count) > entries.size()) {
                count = entries.size() - offset;
            }
            return new RangeIterator<String>(entries.keySet().iterator(), offset, count);
        }
    }

    @Override
    public Iterator<ChildNodeEntry> getEntries(int offset, int count) {
        if (offset < 0 || count < -1) {
            throw new IllegalArgumentException();
        }
        if (offset == 0 && count == -1) {
            return entries.values().iterator();
        } else {
            if (offset >= entries.size() || count == 0) {
                return EMPTY_ITER;
            }
            if (count == -1 || (offset + count) > entries.size()) {
                count = entries.size() - offset;
            }
            return new RangeIterator<ChildNodeEntry>(entries.values().iterator(), offset, count);
        }
    }

    //------------------------------------------------------------< write ops >

    @Override
    public ChildNodeEntry add(ChildNodeEntry entry) {
        return entries.put(entry.getName(), entry);
    }

    @Override
    public ChildNodeEntry remove(String name) {
        return entries.remove(name);
    }

    @Override
    public ChildNodeEntry rename(String oldName, String newName) {
        if (oldName.equals(newName)) {
            return entries.get(oldName);
        }
        if (entries.get(oldName) == null) {
            return null;
        }
        HashMap<String, ChildNodeEntry> clone =
                (HashMap<String, ChildNodeEntry>) entries.clone();
        entries.clear();
        ChildNodeEntry oldCNE = null;
        for (Map.Entry<String, ChildNodeEntry> entry : clone.entrySet()) {
            if (entry.getKey().equals(oldName)) {
                oldCNE = entry.getValue();
                entries.put(newName, new ChildNodeEntry(newName, oldCNE.getId()));
            } else {
                entries.put(entry.getKey(), entry.getValue());
            }
        }
        return oldCNE;
    }

    //-------------------------------------------------------------< diff ops >

    @Override
    public Iterator<ChildNodeEntry> getAdded(final ChildNodeEntries other) {
        if (equals(other)) {
            return EMPTY_ITER;            
        }
        
        return new AbstractFilteringIterator<ChildNodeEntry>(other.getEntries(0, -1)) {
            @Override
            protected boolean include(ChildNodeEntry entry) {
                return !entries.containsKey(entry.getName());
            }
        };
    }

    @Override
    public Iterator<ChildNodeEntry> getRemoved(final ChildNodeEntries other) {
        if (equals(other)) {
            return EMPTY_ITER;
        }

        return new AbstractFilteringIterator<ChildNodeEntry>(entries.values().iterator()) {
            @Override
            protected boolean include(ChildNodeEntry entry) {
                return other.get(entry.getName()) == null;
            }
        };
    }

    @Override
    public Iterator<ChildNodeEntry> getModified(final ChildNodeEntries other) {
        if (equals(other)) {
            return EMPTY_ITER;
        }
        return new AbstractFilteringIterator<ChildNodeEntry>(getEntries(0, -1)) {
            @Override
            protected boolean include(ChildNodeEntry entry) {
                ChildNodeEntry namesake = other.get(entry.getName());
                return (namesake != null && !namesake.getId().equals(entry.getId()));
            }
        };
    }

    //------------------------------------------------< serialization support >

    @Override
    public void serialize(Binding binding) throws Exception {
        binding.write(":ordered", insertionOrdered ? 1 : 0);
        final Iterator<ChildNodeEntry> iter = getEntries(0, -1);
        binding.writeMap(":children", getCount(),
                new Binding.BytesEntryIterator() {
                    @Override
                    public boolean hasNext() {
                        return iter.hasNext();
                    }
                    @Override
                    public Binding.BytesEntry next() {
                        ChildNodeEntry cne = iter.next();
                        return new Binding.BytesEntry(cne.getName(), StringUtils.convertHexToBytes(cne.getId()));
                    }
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                });
    }

    public static ChildNodeEntriesMap deserialize(Binding binding) throws Exception {
        boolean insertionOrdered = binding.readIntValue(":ordered") == 0 ? false : true;
        ChildNodeEntriesMap newInstance = new ChildNodeEntriesMap(insertionOrdered);
        Binding.BytesEntryIterator iter = binding.readBytesMap(":children");
        while (iter.hasNext()) {
            Binding.BytesEntry entry = iter.next();
            newInstance.add(new ChildNodeEntry(entry.getKey(), StringUtils.convertBytesToHex(entry.getValue())));
        }
        return newInstance;
    }
}