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
package org.eclipse.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.dialogs.IDialogSettings;

import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.fix.ControlStatementsFix;
import org.eclipse.jdt.internal.corext.fix.IFix;

import org.eclipse.jdt.ui.text.java.IProblemLocation;

public class ControlStatementsCleanUp extends AbstractCleanUp {
	
	/**
	 * Adds block to control statement body if the body is not a block.<p>
	 * i.e.:<pre><code>
	 * 	 if (b) foo(); -> if (b) {foo();}</code></pre>
	 */
	public static final int ADD_BLOCK_TO_CONTROL_STATEMENTS= 1;
	
	/**
	 * Convert for loops to enhanced for loops.<p>
	 * i.e.:<pre><code>
	 * for (int i = 0; i < array.length; i++) {} -> for (int element : array) {}</code></pre>
	 */
	public static final int CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP= 2;
	
	/**
	 * Remove unnecessary blocks in control statement bodies.<p>
	 * i.e.:<pre><code>
	 *   if (b) {foo();} -> if (b) foo();</code></pre>
	 */
	public static final int REMOVE_UNNECESSARY_BLOCKS= 4;
	
	/**
	 * Remove unnecessary blocks in control statement bodies if they contain
	 * a single return or throw statement.<p>
	 * i.e.:<pre><code>
	 *   if (b) {return;} -> if (b) return;</code></pre>
	 */
	public static final int REMOVE_UNNECESSARY_BLOCKS_CONTAINING_RETURN_OR_THROW= 8;

	private static final int DEFAULT_FLAG= 0;
	private static final String SECTION_NAME= "CleanUp_ControlStatements"; //$NON-NLS-1$


	public ControlStatementsCleanUp(int flag) {
		super(flag);
	}

	public ControlStatementsCleanUp(IDialogSettings settings) {
		super(getSection(settings, SECTION_NAME), DEFAULT_FLAG);
	}

	/**
	 * {@inheritDoc}
	 */
	public IFix createFix(CompilationUnit compilationUnit) throws CoreException {
		if (compilationUnit == null)
			return null;
		
		return ControlStatementsFix.createCleanUp(compilationUnit,
				isFlag(ADD_BLOCK_TO_CONTROL_STATEMENTS),
				isFlag(REMOVE_UNNECESSARY_BLOCKS),
				isFlag(REMOVE_UNNECESSARY_BLOCKS_CONTAINING_RETURN_OR_THROW),
				isFlag(CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP));
	}

	/**
	 * {@inheritDoc}
	 */
	public IFix createFix(CompilationUnit compilationUnit, IProblemLocation[] problems) throws CoreException {
		//No warnings generated by the compiler
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public Map getRequiredOptions() {
		return null;
	}
	
	public void saveSettings(IDialogSettings settings) {
		super.saveSettings(getSection(settings, SECTION_NAME));
	}

	/**
	 * {@inheritDoc}
	 */
	public String[] getDescriptions() {
		List result= new ArrayList();
		if (isFlag(ADD_BLOCK_TO_CONTROL_STATEMENTS))
			result.add(MultiFixMessages.CodeStyleMultiFix_ConvertSingleStatementInControlBodeyToBlock_description);
		if (isFlag(CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP))
			result.add(MultiFixMessages.Java50CleanUp_ConvertToEnhancedForLoop_description);
		if (isFlag(REMOVE_UNNECESSARY_BLOCKS))
			result.add(MultiFixMessages.ControlStatementsCleanUp_RemoveUnnecessaryBlocks_description);
		if (isFlag(REMOVE_UNNECESSARY_BLOCKS_CONTAINING_RETURN_OR_THROW))
			result.add(MultiFixMessages.ControlStatementsCleanUp_RemoveUnnecessaryBlocksWithReturnOrThrow_description);
		
		return (String[])result.toArray(new String[result.size()]);
	}
	
	public String getPreview() {
		StringBuffer buf= new StringBuffer();
		
		if (isFlag(ADD_BLOCK_TO_CONTROL_STATEMENTS)) {
			buf.append("if (obj == null) {\n"); //$NON-NLS-1$
			buf.append("    throw new IllegalArgumentException();\n"); //$NON-NLS-1$
			buf.append("}\n"); //$NON-NLS-1$
			
			buf.append("if (ids.length > 0) {\n"); //$NON-NLS-1$
			buf.append("    System.out.println(ids[0]);\n"); //$NON-NLS-1$
			buf.append("} else {\n"); //$NON-NLS-1$
			buf.append("    return;\n"); //$NON-NLS-1$
			buf.append("}\n"); //$NON-NLS-1$
		} else if (isFlag(REMOVE_UNNECESSARY_BLOCKS)){
			buf.append("if (obj == null)\n"); //$NON-NLS-1$
			buf.append("    throw new IllegalArgumentException();\n"); //$NON-NLS-1$
			buf.append("\n"); //$NON-NLS-1$
			
			buf.append("if (ids.length > 0)\n"); //$NON-NLS-1$
			buf.append("    System.out.println(ids[0]);\n"); //$NON-NLS-1$
			buf.append("else\n"); //$NON-NLS-1$
			buf.append("    return;\n"); //$NON-NLS-1$
			buf.append("\n"); //$NON-NLS-1$
		} else if (isFlag(REMOVE_UNNECESSARY_BLOCKS_CONTAINING_RETURN_OR_THROW)) {
			buf.append("if (obj == null)\n"); //$NON-NLS-1$
			buf.append("    throw new IllegalArgumentException();\n"); //$NON-NLS-1$
			buf.append("\n"); //$NON-NLS-1$
			
			buf.append("if (ids.length > 0) {\n"); //$NON-NLS-1$
			buf.append("    System.out.println(ids[0]);\n"); //$NON-NLS-1$
			buf.append("} else \n"); //$NON-NLS-1$
			buf.append("    return;\n"); //$NON-NLS-1$
			buf.append("\n"); //$NON-NLS-1$
		} else {
			buf.append("if (obj == null) {\n"); //$NON-NLS-1$
			buf.append("    throw new IllegalArgumentException();\n"); //$NON-NLS-1$
			buf.append("}\n"); //$NON-NLS-1$
			
			buf.append("if (ids.length > 0) {\n"); //$NON-NLS-1$
			buf.append("    System.out.println(ids[0]);\n"); //$NON-NLS-1$
			buf.append("} else \n"); //$NON-NLS-1$
			buf.append("    return;\n"); //$NON-NLS-1$
			buf.append("\n"); //$NON-NLS-1$
		}
		buf.append("\n"); //$NON-NLS-1$
		if (isFlag(CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP)) {
			buf.append("for (int element : ids) {\n"); //$NON-NLS-1$
			buf.append("    double value= element / 2; \n"); //$NON-NLS-1$
			buf.append("    System.out.println(value);\n"); //$NON-NLS-1$
			buf.append("}\n"); //$NON-NLS-1$
		} else {
			buf.append("for (int i = 0; i < ids.length; i++) {\n"); //$NON-NLS-1$
			buf.append("    double value= ids[i] / 2; \n"); //$NON-NLS-1$
			buf.append("    System.out.println(value);\n"); //$NON-NLS-1$
			buf.append("}\n"); //$NON-NLS-1$
		}
		
		return buf.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean canFix(CompilationUnit compilationUnit, IProblemLocation problem) throws CoreException {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public int maximalNumberOfFixes(CompilationUnit compilationUnit) {
		return -1;
	}

	/**
	 * {@inheritDoc}
	 */
	public int getDefaultFlag() {
		return DEFAULT_FLAG;
	}



}
