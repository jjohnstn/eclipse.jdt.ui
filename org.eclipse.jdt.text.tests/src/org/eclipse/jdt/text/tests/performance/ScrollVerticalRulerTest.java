/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.text.tests.performance;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.internal.editors.text.EditorsPlugin;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.AnnotationPreference;
import org.eclipse.ui.texteditor.MarkerAnnotationPreferences;


/**
 * @since 3.4
 */
public abstract class ScrollVerticalRulerTest extends ScrollEditorTest {
	
	public static Test suite() {
		TestSuite result= new TestSuite();
		
		result.addTest(ScrollVerticalRuler100Test.suite());
		result.addTest(ScrollVerticalRuler1000Test.suite());
		result.addTest(ScrollVerticalRuler5000Test.suite());
		
		return result;
	}
	
	protected String getEditor() {
		return EditorTestHelper.TEXT_EDITOR_ID;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.text.tests.performance.ScrollEditorTest#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		
		InstanceScope scope= new InstanceScope();
		IEclipsePreferences jdtNode= scope.getNode(JavaUI.ID_PLUGIN);
		jdtNode.putInt(PreferenceConstants.SPELLING_PROBLEMS_THRESHOLD, 100000);
		
		IEclipsePreferences editorsNode= scope.getNode(EditorsUI.PLUGIN_ID);
		
		MarkerAnnotationPreferences markerAnnotationPreferences= EditorsPlugin.getDefault().getMarkerAnnotationPreferences();
		Iterator iterator= markerAnnotationPreferences.getAnnotationPreferences().iterator();
		while (iterator.hasNext()) {
			AnnotationPreference pref= (AnnotationPreference) iterator.next();
			String preferenceKey= pref.getVerticalRulerPreferenceKey();
			if ("spellingIndicationInVerticalRuler".equals(preferenceKey)) {
				editorsNode.putBoolean(preferenceKey, true);
				String textPreferenceKey= pref.getTextPreferenceKey();
				editorsNode.putBoolean(textPreferenceKey, false);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.text.tests.performance.ScrollEditorTest#tearDown()
	 */
	protected void tearDown() throws Exception {
		InstanceScope scope= new InstanceScope();
		IEclipsePreferences jdtNode= scope.getNode(JavaUI.ID_PLUGIN);
		jdtNode.putInt(PreferenceConstants.SPELLING_PROBLEMS_THRESHOLD, 100);
		
		IEclipsePreferences editorsNode= scope.getNode(EditorsUI.PLUGIN_ID);
		
		MarkerAnnotationPreferences markerAnnotationPreferences= EditorsPlugin.getDefault().getMarkerAnnotationPreferences();
		Iterator iterator= markerAnnotationPreferences.getAnnotationPreferences().iterator();
		while (iterator.hasNext()) {
			AnnotationPreference pref= (AnnotationPreference) iterator.next();
			String preferenceKey= pref.getVerticalRulerPreferenceKey();
			if ("spellingIndicationInVerticalRuler".equals(preferenceKey)) {
				editorsNode.putBoolean(preferenceKey, false);
				String textPreferenceKey= pref.getTextPreferenceKey();
				editorsNode.putBoolean(textPreferenceKey, true);
			}
		}
		
		
		super.tearDown();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.text.tests.performance.ScrollEditorTest#openEditor(org.eclipse.jdt.text.tests.performance.ScrollEditorTest.ScrollingMode)
	 */
	protected AbstractTextEditor openEditor(ScrollingMode mode) throws Exception {
		IFile file= ResourceTestHelper.getProject(PerformanceTestSetup.PROJECT).getFile("faust.txt");
		if (!file.exists()) {
			file.create(getFaustInputStream(), true, null);
		}
		
		AbstractTextEditor result= (AbstractTextEditor) EditorTestHelper.openInEditor(file, true);
		
		IDocument document= EditorTestHelper.getDocument(result);
		document.replace(0, document.getLength(), getFaust(getNumberOfAnnotations()));
		
		result.doSave(null);
		
		return result;
	}
	
	private String getFaust(int numberOfLines) throws Exception {
		String faust= FileTool.read(new InputStreamReader(getFaustInputStream())).toString();
		
		IDocument document= new Document(faust);
		int lineOffset= document.getLineOffset(numberOfLines);
		return document.get(0, lineOffset);
	}
	
	private InputStream getFaustInputStream() {
		return AbstractDocumentPerformanceTest.class.getResourceAsStream("faust1.txt");
	}
	
	/**
	 * @return the number of annotations to show in the vertical ruler
	 */
	protected abstract int getNumberOfAnnotations();
	
}