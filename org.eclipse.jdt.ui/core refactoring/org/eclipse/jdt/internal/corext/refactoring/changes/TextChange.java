/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.changes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditCopier;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.text.edits.TextEditProcessor;
import org.eclipse.text.edits.UndoEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.base.Change;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeAbortException;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;

/**
 * A text change is a special change object that applies a {@link TextEdit
 * text edit tree} to a document. The text change manages the text edit tree. 
 * Access to the document must be provided by concrete subclasses via the method
 * {@link #aquireDocument(IProgressMonitor) aquireDocument} and 
 * {@link #releaseDocument(IDocument, IProgressMonitor) releaseDocument}.
 * <p>
 * A text change offers the ability to access the original content of
 * the document as well as creating a preview of the change. The edit
 * tree gets copied when creating any king of preview. Therefore no region
 * updating on the original edit tree takes place when requesting a preview
 * (for more information on region unpdating see class {@link TextEdit TextEdit}. 
 * If region tracking is required for a preview it can be enabled via a call 
 * to the method {@link #setKeepPreviewEdits(boolean) setKeepPreviewEdits}.
 * If enabled the text change keeps the copied edit tree executed for the
 * preview allowing clients to map an original edit to an executed edit. The
 * executed edit can then be used to determine its position in the preview.
 * </p>
 * 
 * @since 3.0
 */
public abstract class TextChange extends Change {

	private static class LocalTextEditProcessor extends TextEditProcessor {
		public static final int EXCLUDE= 1;
		public static final int INCLUDE= 2;

		private TextEdit[] fExcludes;
		private TextEdit[] fIncludes;
		
		public LocalTextEditProcessor(IDocument document, TextEdit root, int flags) {
			super(document, root, flags);
		}
		public void setIncludes(TextEdit[] includes) {
			Assert.isNotNull(includes);
			Assert.isTrue(fExcludes == null);
			fIncludes= flatten(includes);
		}
		public void setExcludes(TextEdit[] excludes) {
			Assert.isNotNull(excludes);
			Assert.isTrue(fIncludes == null);
			fExcludes= excludes;
		}
		protected boolean considerEdit(TextEdit edit) {
			if (fExcludes != null) {
				for (int i= 0; i < fExcludes.length; i++) {
					if (edit.equals(fExcludes[i]))
						return false;
				}
				return true;
			}
			if (fIncludes != null) {
				for (int i= 0; i < fIncludes.length; i++) {
					if (edit.equals(fIncludes[i]))
						return true;
				}
				return false;
			}
			return true;
		}
		private TextEdit[] flatten(TextEdit[] edits) {
			List result= new ArrayList(5);
			for (int i= 0; i < edits.length; i++) {
				flatten(result, edits[i]);
			}
			return (TextEdit[])result.toArray(new TextEdit[result.size()]);
		}
		private void flatten(List result, TextEdit edit) {
			result.add(edit);
			TextEdit[] children= edit.getChildren();
			for (int i= 0; i < children.length; i++) {
				flatten(result, children[i]);
			}
		}
	}
	
	private static class PreviewAndRegion {
		public PreviewAndRegion(IDocument d, IRegion r) {
			document= d;
			region= r;
		}
		public IDocument document;
		public IRegion region;
	}
	
	private List fTextEditChangeGroups;
	private TextEditCopier fCopier;
	private TextEdit fEdit;
	private boolean fTrackEdits;
	private String fTextType;
	private IChange fUndoChange;

	/**
	 * A special object denoting all edits managed by the text change. This even 
	 * includes those edits not managed by a <code>TextEditChangeGroup</code> 
	 */
	private static final TextEditChangeGroup[] ALL_EDITS= new TextEditChangeGroup[0]; 
	
	/**
	 * Creates a new text change with the specified name.  The name is a 
	 * human-readable value that is displayed to users.  The name does not 
	 * need to be unique, but it must not be <code>null</code>.
	 * <p>
	 * The text type of this text change is set to <code>txt</code>.
	 * </p>
	 * 
	 * @param name the name of the text change
	 * 
	 * @see #setTextType(String)
	 */
	protected TextChange(String name) {
		super(name);
		fTextEditChangeGroups= new ArrayList(5);
		fTextType= "txt"; //$NON-NLS-1$
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void setActive(boolean active) {
		super.setActive(active);
		for (Iterator iter= fTextEditChangeGroups.iterator(); iter.hasNext();) {
			TextEditChangeGroup element= (TextEditChangeGroup) iter.next();
			element.setActive(active);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public IChange getUndoChange() {
		return fUndoChange;
	}

	/**
	 * Sets the text type. The text type is used to determine the content
	 * merge viewer used to present the difference between the original
	 * and the preview content in the user interface. Content merge viewers
	 * are defined via the extension point <code>org.eclipse.compare.contentMergeViewers</code>.
	 * <p>
	 * The default text type is <code>txt</code>. 
	 * </p>
	 * 
	 * @param type the text type. If <code>null</code> is passed the text type is 
	 *  resetted to the default text type <code>txt</code>.
	 */
	public void setTextType(String type) {
		if (type == null)
			type= "txt"; //$NON-NLS-1$
		fTextType= type;
	}
	
	/**
	 * Returns the text change's text type.
	 * 
	 * @return the text change's text type
	 */
	public String getTextType() {
		return fTextType;
	}
	
	public final RefactoringStatus aboutToPerform(ChangeContext context, IProgressMonitor pm) {
		try {
			return isValid(pm);
		} catch (CoreException e) {
			return RefactoringStatus.createFatalErrorStatus(e.getMessage());
		}
	}

	//---- Edit management -----------------------------------------------
	
	/**
	 * Sets the root text edit that should be applied to the 
	 * document represented by this text change.
	 * 
	 * @param edit the root text edit. The root text edit
	 *  can only be set once. 
	 */
	public void setEdit(TextEdit edit) {
		Assert.isTrue(fEdit == null, "Root edit can only be set once"); //$NON-NLS-1$
		Assert.isTrue(edit != null);
		fEdit= edit;
	}
	
	/**
	 * Returns the root text edit.
	 * 
	 * @return the root text edit
	 */
	public TextEdit getEdit() {
		return fEdit;
	}	
	
	/**
	 * Adds a {@link TextEditGroup text edit group}. This method is a convenient
	 * method for calling <code>change.addTextEditChangeGroup(new 
	 * TextEditChangeGroup(change, group));</code>.
	 * 
	 * @param group the text edit group to add
	 */
	public void addTextEditGroup(TextEditGroup group) {
		addTextEditChangeGroup(new TextEditChangeGroup(this, group));
	}
	
	/**
	 * Adds a {@link TextEditChangeGroup text edit change group}. Calling the methods 
	 * requires that a root edit has been set via the method {@link #setEdit(TextEdit)
	 * setEdit}. The edits managed by the given text edit change group must be part of 
	 * the change's root edit. 
	 * 
	 * @param group the text edit change group to add
	 */
	public void addTextEditChangeGroup(TextEditChangeGroup group) {
		Assert.isTrue(fEdit != null, "Can only add a description if a root edit exists"); //$NON-NLS-1$
		Assert.isTrue(group != null);
		fTextEditChangeGroups.add(group);
	}
	
	/**
	 * Returns the {@link TextEditChangeGroup text edit change groups} managed by this 
	 * text change.
	 * 
	 * @return the text edit change groups
	 */
	public TextEditChangeGroup[] getTextEditChangeGroups() {
		return (TextEditChangeGroup[])fTextEditChangeGroups.toArray(new TextEditChangeGroup[fTextEditChangeGroups.size()]);
	}
	
	/**
	 * Aquires a reference to the document to be changed by this text
	 * change. A document aquired by this call <em>MUST</em> be released
	 * via a call to {@link #releaseDocument(IDocument, IProgressMonitor)
	 * releaseDocument}.
	 * 
	 * @param pm a progress monitor
	 * 
	 * @return a reference to the document to be changed
	 * 
	 * @throws CoreException if the document can't be aquired
	 */
	protected abstract IDocument aquireDocument(IProgressMonitor pm) throws CoreException;
	
	/**
	 * Releases the document aquired via a call to {@link #aquireDocument(IProgressMonitor)
	 * aquireDocument}.
	 * 
	 * @param document the document to release
	 * @param pm a progress monitor
	 * 
	 * @throws CoreException if the document can't be released
	 */
	protected abstract void releaseDocument(IDocument document, IProgressMonitor pm) throws CoreException;
	
	/**
	 * Hook to create a corresponding undo change for the given edit 
	 * when performing this change object.
	 * 
	 * @param edit the {@link UndoEdit undo edit} to create a undo change
	 *  object for
	 * 
	 * @return the undo change
	 * 
	 * @throws CoreException if an undo change can't be created
	 */
	protected abstract IChange createUndoChange(UndoEdit edit) throws CoreException;
	
	/**
	 * {@inheritDoc}
	 */
	public void perform(ChangeContext context, IProgressMonitor pm) throws JavaModelException, ChangeAbortException {
		pm.beginTask("", 2); //$NON-NLS-1$
		try {
			IDocument document= null;
			try {
				document= aquireDocument(new SubProgressMonitor(pm, 1));
				TextEditProcessor processor= createTextEditProcessor(document, TextEdit.CREATE_UNDO, false);
				UndoEdit undo= processor.performEdits();
				fUndoChange= createUndoChange(undo);
			} catch (BadLocationException e) {
				Changes.asCoreException(e);
			} finally {
				if (document != null)
					releaseDocument(document, new SubProgressMonitor(pm, 1));
				pm.done();
			}
		} catch (CoreException e) {
			throw new JavaModelException(e);
		}
	}
	
	//---- Method to access the current content of the text change ---------

	/**
	 * Returns the current content of the document this text
	 * change is applied to.
	 * 
	 * @return the current content of the text change
	 * 
	 * @exception CoreException if the content can't be accessed
	 */
	public String getCurrentContent() throws CoreException {
		return getDocument().get();
	}
	
	/**
	 * Returns the current content of the text change clipped to a specific
	 * region. The region is determined as follows:
	 * <ul>
	 *   <li>if <code>expandRegionToFullLine</code> is <code>false</code>
	 *       then the parameter <code>region</code> determines the clipping.
	 *   </li>
	 *   <li>if <code>expandRegionToFullLine</code> is <code>true</code>
	 *       then the region determined by the parameter <code>region</code>
	 *       is extended to cover full lines. 
	 *   </li>
	 *   <li>if <code>surroundingLines</code> &gt; 0 then the given number
	 *       of surrounding lines is added. The value of <code>surroundingLines
	 *       </code> is only considered if <code>expandRegionToFullLine</code>
	 *       is <code>true</code>
	 *   </li>
	 * </ul> 
	 * 
	 * @param region the starting region for the clipping
	 * @param expandRegionToFullLine if <code>true</code> is passed the region
	 *  is extended to cover full lines
	 * @param surroundingLines the number of surrounding lines to be added to 
	 *  the clipping region. Is only considered if <code>expandRegionToFullLine
	 *  </code> is <code>true</code>
	 * 
	 * @return the current content of the text change clipped to a region
	 *  determined by the given parameters.
	 * 
	 * @throws CoreException
	 */
	public String getCurrentContent(IRegion region, boolean expandRegionToFullLine, int surroundLines) throws CoreException {
		Assert.isNotNull(region);
		Assert.isTrue(surroundLines >= 0);
		IDocument document= getDocument();
		Assert.isTrue(document.getLength() >= region.getOffset() + region.getLength());
		return getContent(document, region, expandRegionToFullLine, 0);
	}

	//---- Method to access the preview content of the text change ---------

	/**
	 * Controls whether the text change should keep executed edits during 
	 * preview generation.
	 * 
	 * @param keep if <code>true</code> executed preview edits are kept
	 */
	public void setKeepPreviewEdits(boolean keep) {
		fTrackEdits= keep;
		if (!fTrackEdits)
			fCopier= null;
	}
	
	/**
	 * Returns whether preview edits are remembered for further region
	 * tracking or not.
	 * 
	 * @return <code>true</code> if executed text edits are remembered
	 * during preview generation; otherwise <code>false</code>
	 */
	public boolean getKeepPreviewEdits() {
		return fTrackEdits;
	}
	
	/**
	 * Returns the edit that got executed during preview generation
	 * instead of the given orignial. The method requires that <code>
	 * setKeepPreviewEdits</code> is set to <code>true</code> and that 
	 * a preview has been requested via one of the <code>getPreview*
	 * </code> methods.
	 * <p>
	 * The method returns <code>null</code> if the original isn't managed
	 * by this text change.
	 * </p>
	 * 
	 * @param original the original edit managed by this text change
	 * 
	 * @return the edit executed during preview generation
	 */
	public TextEdit getPreviewEdit(TextEdit original) {
		Assert.isTrue(fTrackEdits && fCopier != null && original != null);
		return fCopier.getCopy(original);
	}
	
	/**
	 * Returns the edits that were executed during preview generation
	 * instead of the given array of orignial edits. The method requires 
	 * that <code>setKeepPreviewEdits</code> is set to <code>true</code> 
	 * and that a preview has been requested via one of the <code>
	 * getPreview*</code> methods.
	 * <p>
	 * The method returns an empty array if none of the original edits
	 * is managed by this text change.
	 * </p>
	 * 
	 * @param original an array of original edits managed by this text
	 *  change
	 * 
	 * @return an array of edits containing the corresponding edits 
	 *  executed during preview generation
	 */
	public TextEdit[] getPreviewEdits(TextEdit[] originals) {
		Assert.isTrue(fTrackEdits && fCopier != null && originals != null);
		if (originals.length == 0)
			return new TextEdit[0];
		List result= new ArrayList(originals.length);
		for (int i= 0; i < originals.length; i++) {
			TextEdit copy= fCopier.getCopy(originals[i]);
			if (copy != null)
				result.add(copy);
		}
		return (TextEdit[]) result.toArray(new TextEdit[result.size()]);
	}
	
	/**
	 * Returns a document containing a preview of the text change. The
	 * preview is computed by executing the all managed text edits. The
	 * method considers the active state of the added {@link TextEditChangeGroup
	 * text edit change groups}.
	 * 
	 * @return a document containing the preview of the text change
	 * 
	 * @throws CoreException if the preview can't be created
	 */
	public IDocument getPreviewDocument() throws CoreException {
		PreviewAndRegion result= getPreviewDocument(ALL_EDITS);
		return result.document;
	}
	
	/**
	 * Returns the preview content as a string. This is a convenient
	 * method for calling <code>getPreviewDocument().get()</code>.
	 * 
	 * @return the preview 
	 * 
	 * @throws CoreException if the preview can't be created
	 */
	public String getPreviewContent() throws CoreException {
		return getPreviewDocument().get();
	}
	
	/**
	 * Returns a preview of the text change clipped to a specific region.
	 * The preview is created by appying the text edits managed by the
	 * given array of {@link TextEditChangeGroup text edit change groups}. 
	 * The region is determined as follows:
	 * <ul>
	 *   <li>if <code>expandRegionToFullLine</code> is <code>false</code>
	 *       then the parameter <code>region</code> determines the clipping.
	 *   </li>
	 *   <li>if <code>expandRegionToFullLine</code> is <code>true</code>
	 *       then the region determined by the parameter <code>region</code>
	 *       is extended to cover full lines. 
	 *   </li>
	 *   <li>if <code>surroundingLines</code> &gt; 0 then the given number
	 *       of surrounding lines is added. The value of <code>surroundingLines
	 *       </code> is only considered if <code>expandRegionToFullLine</code>
	 *       is <code>true</code>
	 *   </li>
	 * </ul> 
	 * 
	 * @param region the starting region for the clipping
	 * @param expandRegionToFullLine if <code>true</code> is passed the region
	 *  is extended to cover full lines
	 * @param surroundingLines the number of surrounding lines to be added to 
	 *  the clipping region. Is only considered if <code>expandRegionToFullLine
	 *  </code> is <code>true</code>
	 * 
	 * @return the current content of the text change clipped to a region
	 *  determined by the given parameters.
	 * 
	 * @throws CoreException
	 * 
	 * @see #getCurrentContent(IRegion, boolean, int)
	 */
	public String getPreviewContent(TextEditChangeGroup[] changes, IRegion region, boolean expandRegionToFullLine, int surroundingLines) throws CoreException {
		IRegion currentRegion= getRegion(changes);
		Assert.isTrue(region.getOffset() <= currentRegion.getOffset() && 
			currentRegion.getOffset() + currentRegion.getLength() <= region.getOffset() + region.getLength());
		PreviewAndRegion result= getPreviewDocument(changes);
		int delta= result.region.getLength() - currentRegion.getLength();
		return getContent(result.document, new Region(region.getOffset(), region.getLength() + delta), expandRegionToFullLine, surroundingLines);
		
	}

	//---- private helper methods --------------------------------------------------
	
	private PreviewAndRegion getPreviewDocument(TextEditChangeGroup[] changes) throws CoreException {
		IDocument document= new Document(getDocument().get());
		boolean trackChanges= fTrackEdits;
		setKeepPreviewEdits(true);
		TextEditProcessor processor= changes == ALL_EDITS
			? createTextEditProcessor(document, TextEdit.NONE, true)
			: createTextEditProcessor(document, TextEdit.NONE, changes);
		try {
			processor.performEdits();
			return new PreviewAndRegion(document, getNewRegion(changes));
		} catch (BadLocationException e) {
			throw Changes.asCoreException(e);
		} finally {
			setKeepPreviewEdits(trackChanges);
		}
	}
	
	private TextEditProcessor createTextEditProcessor(IDocument document, int flags, boolean preview) throws CoreException {
		if (fEdit == null)
			return new TextEditProcessor(document, new MultiTextEdit(0,0), flags);
		List excludes= new ArrayList(0);
		for (Iterator iter= fTextEditChangeGroups.iterator(); iter.hasNext(); ) {
			TextEditChangeGroup edit= (TextEditChangeGroup)iter.next();
			if (!edit.isActive()) {
				excludes.addAll(Arrays.asList(edit.getTextEditGroup().getTextEdits()));
			}
		}
		if (preview) {
			fCopier= new TextEditCopier(fEdit);
			TextEdit copiedEdit= fCopier.perform();
			if (fTrackEdits)
				flags= flags | TextEdit.UPDATE_REGIONS;
			LocalTextEditProcessor result= new LocalTextEditProcessor(document, copiedEdit, flags);
			result.setExcludes(mapEdits(
				(TextEdit[])excludes.toArray(new TextEdit[excludes.size()]),
				fCopier));	
			if (!fTrackEdits)
				fCopier= null;
			return result;
		} else {
			LocalTextEditProcessor result= new LocalTextEditProcessor(document, fEdit, flags | TextEdit.UPDATE_REGIONS);
			result.setExcludes((TextEdit[])excludes.toArray(new TextEdit[excludes.size()]));
			return result;
		}
	}
	
	private TextEditProcessor createTextEditProcessor(IDocument document, int flags, TextEditChangeGroup[] changes) throws CoreException {
		if (fEdit == null)
			return new TextEditProcessor(document, new MultiTextEdit(0,0), flags);
		List includes= new ArrayList(0);
		for (int c= 0; c < changes.length; c++) {
			TextEditChangeGroup change= changes[c];
			Assert.isTrue(change.getTextChange() == this);
			if (change.isActive()) {
				includes.addAll(Arrays.asList(change.getTextEditGroup().getTextEdits()));
			}
		}
		fCopier= new TextEditCopier(fEdit);
		TextEdit copiedEdit= fCopier.perform();
		if (fTrackEdits)
			flags= flags | TextEdit.UPDATE_REGIONS;
		LocalTextEditProcessor result= new LocalTextEditProcessor(document, copiedEdit, flags);
		result.setIncludes(mapEdits(
			(TextEdit[])includes.toArray(new TextEdit[includes.size()]),
			fCopier));
		if (!fTrackEdits)
			fCopier= null;
		return result;
	}
	
	private IDocument getDocument() throws CoreException {
		IDocument result= aquireDocument(new NullProgressMonitor());
		releaseDocument(result, new NullProgressMonitor());
		return result;
	}
	
	private TextEdit[] mapEdits(TextEdit[] edits, TextEditCopier copier) {
		if (edits == null)
			return null;
		for (int i= 0; i < edits.length; i++) {
			edits[i]= copier.getCopy(edits[i]);
		}
		return edits;
	}
	
	private String getContent(IDocument document, IRegion region, boolean expandRegionToFullLine, int surroundingLines) throws CoreException {
		try {
			if (expandRegionToFullLine) {
				int startLine= Math.max(document.getLineOfOffset(region.getOffset()) - surroundingLines, 0);
				int endLine= Math.min(
					document.getLineOfOffset(region.getOffset() + region.getLength() - 1) + surroundingLines,
					document.getNumberOfLines() - 1);
				
				int offset= document.getLineInformation(startLine).getOffset();
				IRegion endLineRegion= document.getLineInformation(endLine);
				int length = endLineRegion.getOffset() + endLineRegion.getLength() - offset;
				return document.get(offset, length);
				
			} else {
				return document.get(region.getOffset(), region.getLength());
			}
		} catch (BadLocationException e) {
			throw Changes.asCoreException(e);
		}
	}
	
	private IRegion getRegion(TextEditChangeGroup[] changes) {
		if (changes == ALL_EDITS) {
			if (fEdit == null)
				return null;
			return fEdit.getRegion();
		} else {
			List edits= new ArrayList();
			for (int i= 0; i < changes.length; i++) {
				edits.addAll(Arrays.asList(changes[i].getTextEditGroup().getTextEdits()));
			}
			if (edits.size() == 0)
				return null;
			return TextEdit.getCoverage((TextEdit[]) edits.toArray(new TextEdit[edits.size()]));
		}
	}
	
	private IRegion getNewRegion(TextEditChangeGroup[] changes) {
		if (changes == ALL_EDITS) {
			if (fEdit == null)
				return null;
			return fCopier.getCopy(fEdit).getRegion();
		} else {
			List result= new ArrayList();
			for (int c= 0; c < changes.length; c++) {
				TextEdit[] edits= changes[c].getTextEditGroup().getTextEdits();
				for (int e= 0; e < edits.length; e++) {
					TextEdit copy= fCopier.getCopy(edits[e]);
					if (copy != null)
						result.add(copy);
				}
			}
			if (result.size() == 0)
				return null;
			return TextEdit.getCoverage((TextEdit[]) result.toArray(new TextEdit[result.size()]));
		}
	}
}

