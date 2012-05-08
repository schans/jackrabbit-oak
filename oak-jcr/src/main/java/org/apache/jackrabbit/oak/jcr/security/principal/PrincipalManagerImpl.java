/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.jcr.security.principal;

import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.oak.security.principal.EveryonePrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;

/**
 * PrincipalManagerImpl...
 */
public class PrincipalManagerImpl implements PrincipalManager {

    /**
     * logger instance
     */
    private static final Logger log = LoggerFactory.getLogger(PrincipalManagerImpl.class);

    @Override
    public boolean hasPrincipal(String principalName) {
        // TODO
        return false;
    }

    @Override
    public Principal getPrincipal(String principalName) {
        // TODO
        return null;
    }

    @Override
    public PrincipalIterator findPrincipals(String simpleFilter) {
        // TODO
        return null;
    }

    @Override
    public PrincipalIterator findPrincipals(String simpleFilter, int searchType) {
        // TODO
        return null;
    }

    @Override
    public PrincipalIterator getPrincipals(int searchType) {
        // TODO
        return null;
    }

    @Override
    public PrincipalIterator getGroupMembership(Principal principal) {
        // TODO
        return null;
    }

    @Override
    public Principal getEveryone() {
        Principal everyone = getPrincipal(EveryonePrincipal.NAME);
        if (everyone == null) {
            everyone = EveryonePrincipal.getInstance();
        }
        return everyone;
    }
}