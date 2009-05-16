/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2001-2002 Kana Software, Inc.
// Copyright (C) 2001-2009 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, 22 December, 2001
*/

package mondrian.rolap;

import mondrian.olap.Id;
import mondrian.olap.Util;
import mondrian.resource.MondrianResource;

import java.util.Collections;
import java.util.List;

/**
 * <code>ArrayMemberSource</code> implements a flat, static hierarchy. There is
 * no root member, and all members are siblings.
 *
 * @author jhyde
 * @since 22 December, 2001
 * @version $Id$
 */
abstract class ArrayMemberSource implements MemberSource {

    protected final RolapHierarchy hierarchy;
    protected final List<RolapMember> members;

    ArrayMemberSource(RolapHierarchy hierarchy, List<RolapMember> members) {
        this.hierarchy = hierarchy;
        this.members = members;
    }
    public RolapHierarchy getHierarchy() {
        return hierarchy;
    }
    public boolean setCache(MemberCache cache) {
        return false; // we do not support cache writeback
    }
    public List<RolapMember> getMembers() {
        return members;
    }
    public int getMemberCount() {
        return members.size();
    }

    public List<RolapMember> getRootMembers() {
        return Collections.emptyList();
    }

    public void getMemberChildren(RolapMember parentMember, List<RolapMember> children) {
        // there are no children
    }
    public void getMemberChildren(List<RolapMember> parentMembers, List<RolapMember> children) {
        // there are no children
    }
    public RolapMember lookupMember(List<Id.Segment> uniqueNameParts,
                                    boolean failIfNotFound) {
        String uniqueName = Util.implode(uniqueNameParts);
        for (RolapMember member : members) {
            if (member.getUniqueName().equals(uniqueName)) {
                return member;
            }
        }
        if (failIfNotFound) {
            throw MondrianResource.instance().MdxCantFindMember.ex(uniqueName);
        } else {
            return null;
        }
    }
}

// End ArrayMemberSource.java
