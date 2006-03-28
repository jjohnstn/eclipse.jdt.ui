/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.correction;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.ProgressMonitorDialog;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;

import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;

import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.corext.fix.IFix;
import org.eclipse.jdt.internal.corext.fix.LinkedFix;
import org.eclipse.jdt.internal.corext.fix.PositionGroup;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.text.java.IInvocationContext;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.ICleanUp;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringExecutionHelper;

/**
 * A correction proposal which uses an {@link IFix} to
 * fix a problem. A fix correction proposal may have an {@link ICleanUp}
 * attached which can be executed instead of the provided IFix.
 */
public class FixCorrectionProposal extends LinkedCorrectionProposal implements ICompletionProposalExtension2, IStatusLineProposal {
	
	private final IFix fFix;
	private final ICleanUp fCleanUp;
	private CompilationUnit fCompilationUnit;
	
	public FixCorrectionProposal(IFix fix, ICleanUp cleanUp, int relevance, Image image, IInvocationContext context) {
		super(fix.getDescription(), fix.getCompilationUnit(), null, relevance, image);
		fFix= fix;
		fCleanUp= cleanUp;
		fCompilationUnit= context.getASTRoot();
	}
	
	public ICleanUp getCleanUp() {
		return fCleanUp;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.CUCorrectionProposal#createTextChange()
	 */
	protected TextChange createTextChange() throws CoreException {
		IFix fix= fFix;
		TextChange createChange= fix.createChange();
		if (createChange instanceof TextFileChange)
			((TextFileChange)createChange).setSaveMode(TextFileChange.LEAVE_DIRTY);
		
		if (fix instanceof LinkedFix) {
			LinkedFix linkedFix= (LinkedFix)fix;
			
			PositionGroup[] positionGroups= linkedFix.getPositionGroups();
			for (int i= 0; i < positionGroups.length; i++) {
				PositionGroup group= positionGroups[i];
				String groupId= group.getGroupId();

				ITrackedNodePosition firstPosition= group.getFirstPosition();
				ITrackedNodePosition[] positions= group.getPositions();
				for (int j= 0; j < positions.length; j++) {
					addLinkedPosition(positions[j], positions[j] == firstPosition, groupId);
				}
				String[] proposals= group.getProposals();
				String[] displayStrings= group.getDisplayStrings();
				for (int j= 0; j < proposals.length; j++) {
					String proposal= proposals[j];
					String displayString= displayStrings[j];
					
					if (proposal == null)
						proposal= displayString;
					
					if (displayString == null)
						displayString= proposal;
					
					addLinkedPositionProposal(groupId, displayString, proposal, null);
				}
			}
			
			ITrackedNodePosition endPosition= linkedFix.getEndPosition();
			if (endPosition != null) {
				setEndPosition(endPosition);
			}
		}
		
		if (createChange == null)
			return new CompilationUnitChange("", getCompilationUnit()); //$NON-NLS-1$
		
		return createChange;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposalExtension2#apply(org.eclipse.jface.text.ITextViewer, char, int, int)
	 */
	public void apply(ITextViewer viewer, char trigger, int stateMask, int offset) {
		if (stateMask == SWT.CONTROL && fCleanUp != null){
			CleanUpRefactoring refactoring= new CleanUpRefactoring();
			refactoring.addCompilationUnit(getCompilationUnit());
			refactoring.addCleanUp(fCleanUp);
			refactoring.setLeaveFilesDirty(true);
			
			int stopSeverity= RefactoringCore.getConditionCheckingFailedSeverity();
			Shell shell= JavaPlugin.getActiveWorkbenchShell();
			ProgressMonitorDialog context= new ProgressMonitorDialog(shell);
			RefactoringExecutionHelper executer= new RefactoringExecutionHelper(refactoring, stopSeverity, false, shell, context);
			try {
				executer.perform(true);
			} catch (InterruptedException e) {
			} catch (InvocationTargetException e) {
				JavaPlugin.log(e);
			}
			return;
		}
		apply(viewer.getDocument());
	}

	public void selected(ITextViewer viewer, boolean smartToggle) {
	}

	public void unselected(ITextViewer viewer) {
	}

	public boolean validate(IDocument document, int offset, DocumentEvent event) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getStatusMessage() {
		if (fCleanUp == null)
			return null;
		
		int count= fCleanUp.maximalNumberOfFixes(fCompilationUnit);
		if (count == -1) {
			return CorrectionMessages.FixCorrectionProposal_HitCtrlEnter_description;
		} else if (count < 2) {
			return ""; //$NON-NLS-1$
		} else {
			return Messages.format(CorrectionMessages.FixCorrectionProposal_hitCtrlEnter_variable_description, new Integer(count));
		}
	}

}
