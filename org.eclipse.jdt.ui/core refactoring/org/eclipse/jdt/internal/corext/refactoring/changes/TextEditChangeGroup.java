/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.changes;

import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jface.text.IRegion;

import org.eclipse.jdt.internal.corext.Assert;

/**
 * This class is a wrapper around a {@link TextEditGroup TextEditGroup}
 * adding support for marking a group as active and inactive.
 * 
 * @see TextEditGroup
 * 
 * @since 3.0
 */
public class TextEditChangeGroup {
	
	private boolean fIsActive;
	private TextChange fTextChange;
	private TextEditGroup fTextEditGroup;
	
	/**
	 * Creates new <code>TextEditChangeGroup</code> for the given <code>
	 * TextChange</code> and <code>TextEditGroup</code>.
	 * 
	 * @param change the change owning this text edit change group
	 * @param group the underlying text edit group
	 */
	public TextEditChangeGroup(TextChange change, TextEditGroup group) {
		Assert.isNotNull(change);
		Assert.isNotNull(group);
		fTextChange= change;
		fIsActive= true;
		fTextEditGroup= group;
	}
	
	/**
	 * Returns the groups's name by forwarding the method
	 * to the underlying text edit group.
	 * 
	 * @return the group's name
	 */
	public String getName() {
		return fTextEditGroup.getName();
	}
	
	/**
	 * Marks the group as active or inactive. If a group
	 * is marked as inactive the text edits managed by the
	 * underlying text edit group aren't executed when
	 * performing the text change that owns this group.
	 * 
	 * @param active <code>true</code> to mark this group
	 *  as active, <code>false</code> to mark it as inactive
	 */
	public void setActive(boolean active) {
		fIsActive= active;
	}
	
	/**
	 * Returns whether the group is active or not.
	 * 
	 * @return <code>true</code> if the group is marked as
	 *  active; <code>false</code> otherwise
	 */
	public boolean isActive() {
		return fIsActive;
	}
	
	/**
	 * Returns the text change this group belongs to.
	 * 
	 * @return the text change this group belongs to
	 */
	public TextChange getTextChange() {
		return fTextChange;
	}
	
	/**
	 * Returns the underlying text edit group.
	 * 
	 * @return the underlying text edit group
	 */
	public TextEditGroup getTextEditGroup() {
		return fTextEditGroup;
	}
	
	/**
	 * Returns the region covered by the underlying 
	 * text edit group.
	 * 
	 * @return the region covered by the underlying
	 *  text edit group
	 */
	public IRegion getRegion() {
		return fTextEditGroup.getRegion();
	}
	
	/**
	 * Returns the text edits managed by the underlying
	 * text edit group.
	 * 
	 * @return the text edits managed by the underlying
	 *  text edit group
	 */
	public TextEdit[] getTextEdits() {
		return fTextEditGroup.getTextEdits();
	}	
}