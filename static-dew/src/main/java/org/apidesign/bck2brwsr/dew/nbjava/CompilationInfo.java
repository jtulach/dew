/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 * 
 * Contributor(s):
 * 
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */

package org.apidesign.bck2brwsr.dew.nbjava;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.ClientCodeWrapper.Trusted;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.parser.Scanner;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.Tokens;
import org.apidesign.bck2brwsr.dew.javac.JavacEndpoint;

/**
 *
 * @author Tomas Zezula
 */
public final class CompilationInfo {

    private static final Logger LOGGER = Logger.getLogger(JavacEndpoint.class.getName());
    private Phase phase = Phase.MODIFIED;
    private CompilationUnitTree compilationUnit;

    private JavacTaskImpl javacTask;
    private ElementUtilities elementUtilities;
    private TreeUtilities treeUtilities;
    private TypeUtilities typeUtilities;
//    private final ClasspathInfo cpInfo;
//    private final FileObject file;
//    private final FileObject root;
    private final JavaFileObject jfo;
    private final JavaFileManager jfm;
    private DiagnosticListenerImpl diagnosticListener = new DiagnosticListenerImpl();
//    //@NotThreadSafe    //accessed under parser lock
//    private Snapshot snapshot;
//    private final JavacParser parser;
    private final boolean isClassFile = false;
//    private final boolean isDetached;
    Phase parserCrashed = Phase.UP_TO_DATE;      //When javac throws an error, the moveToPhase sets this to the last safe phase

//    private final Map<CacheClearPolicy, Map<Object, Object>> userCache = new EnumMap<CacheClearPolicy, Map<Object, Object>>(CacheClearPolicy.class);
    
    public CompilationInfo(JavaFileObject jfo, JavaFileManager jfm) {
        this.jfo = jfo;
        this.jfm = jfm;
    }
//
//    public Snapshot getSnapshot () {
//        return this.snapshot;
//    }
    
    /**
     * Returns the current phase of the {@link JavaSource}.
     * @return {@link JavaSource.Phase} the state which was reached by the {@link JavaSource}.
     */
    public Phase getPhase() {
        return this.phase;
    }
    
    /**
     * Returns the javac tree representing the source file.
     * @return {@link CompilationUnitTree} the compilation unit cantaining the top level classes contained in the,
     * java source file. 
     * @throws java.lang.IllegalStateException  when the phase is less than {@link JavaSource.Phase#PARSED}
     */
    public CompilationUnitTree getCompilationUnit() {
        if (this.jfo == null) {
            throw new IllegalStateException ();
        }
        if (this.phase.compareTo (Phase.PARSED) < 0)
            throw new IllegalStateException("Cannot call getCompilationUnit() if current phase < JavaSource.Phase.PARSED. You must call toPhase(Phase.PARSED) first.");//NOI18N
        return this.compilationUnit;
    }
    
    /**
     * Returns the content of the file represented by the {@link JavaSource}.
     * @return String the java source
     */
    public String getText() {
        if (!hasSource()) {
            throw new IllegalStateException ();
        }
        try {
            return this.jfo.getCharContent(false).toString();
        } catch (IOException ioe) {
            //Should never happen
            return null;
        }
    }
    
    public List<? extends Pair<String, Integer>> getTokens(int start, int end) {
        if (!hasSource()) {
            throw new IllegalStateException ();
        }
        if (start < end) {
            try {
                ArrayList<Pair<String, Integer>> ret = new ArrayList<>();
                ScannerFactory sf = ScannerFactory.instance(getJavacTask().getContext());
                Scanner s = sf.newScanner(this.jfo.getCharContent(false).subSequence(start, end), false);
                while(true) {
                    s.nextToken();
                    Tokens.Token t = s.token();
                    if (t == null || t.kind == Tokens.TokenKind.EOF)
                        break;
                    switch (t.kind) {
                        case DOT: case COMMA: case SEMI: case LPAREN: case RPAREN:
                        case LBRACKET: case RBRACKET: case LBRACE: case RBRACE:
                            ret.add(new Pair(t.kind.name, start + t.pos));
                            break;
                        default:
                            ret.add(new Pair(t.kind.toString(), start + t.pos));
                    }
                }
                return ret;
            } catch (IOException ioe) {}
        }
        return null;
    }
//    /**
//     * Returns the {@link TokenHierarchy} for the file represented by the {@link JavaSource}.
//     * @return lexer TokenHierarchy
//     */
//    public TokenHierarchy<?> getTokenHierarchy() {
//        if (!hasSource()) {
//            throw new IllegalStateException ();
//        }
//        try {
//            return ((SourceFileObject) this.jfo).getTokenHierarchy();
//        } catch (IOException ioe) {
//            //Should never happen
//            Exceptions.printStackTrace(ioe);
//            return null;
//        }
//    }
//
    /**
     * Returns the errors in the file represented by the {@link JavaSource}.
     * @return an list of {@link Diagnostic} 
     */
    public List<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
        if (this.jfo == null) {
            throw new IllegalStateException ();
        }
        return diagnosticListener.getErrors(this.jfo);
    }
    
    /**
     * Returns all top level elements defined in file for which the {@link CompilationInfo}
     * was created. The {@link CompilationInfo} has to be in phase {@link JavaSource#Phase#ELEMENTS_RESOLVED}.
     * @return list of top level elements, it may return null when this {@link CompilationInfo} is not
     * in phase {@link JavaSource#Phase#ELEMENTS_RESOLVED} or higher.
     * @throws IllegalStateException is thrown when the {@link JavaSource} was created with no files
     * @since 0.14
     */
    public List<? extends TypeElement> getTopLevelElements () throws IllegalStateException {
//        if (this.getFileObject() == null) {
//            throw new IllegalStateException ();
//        }
        final List<TypeElement> result = new ArrayList<>();
//        if (this.impl.isClassFile()) {
//            Elements elements = getElements();
//            assert elements != null;
//            assert this.impl.getRoot() != null;
//            String name = FileObjects.convertFolder2Package(FileObjects.stripExtension(FileUtil.getRelativePath(this.impl.getRoot(), this.impl.getFileObject())));
//            TypeElement e = ((JavacElements)elements).getTypeElementByBinaryName(name);
//            if (e != null) {                
//                result.add (e);
//            }
//        }
//        else {
            CompilationUnitTree cu = getCompilationUnit();
            if (cu == null) {
                return null;
            }
            else {
                final Trees ts = getTrees();
                assert ts != null;
                List<? extends Tree> typeDecls = cu.getTypeDecls();
                TreePath cuPath = new TreePath(cu);
                for( Tree t : typeDecls ) {
                    TreePath p = new TreePath(cuPath,t);
                    Element e = ts.getElement(p);
                    if ( e != null && ( e.getKind().isClass() || e.getKind().isInterface() ) ) {
                        result.add((TypeElement)e);
                    }
                }
            }
//        }
        return Collections.unmodifiableList(result);
    }
        
    
    /**
     * Return the {@link Trees} service of the javac represented by this {@link CompilationInfo}.
     * @return javac Trees service
     */
    public Trees getTrees() {
        return JavacTrees.instance(getJavacTask().getContext());
    }
    
    /**
     * Return the {@link Types} service of the javac represented by this {@link CompilationInfo}.
     * @return javac Types service
     */
    public Types getTypes() {
        return getJavacTask().getTypes();
    }
    
    /**
     * Return the {@link Elements} service of the javac represented by this {@link CompilationInfo}.
     * @return javac Elements service
     */
    public Elements getElements() {
	return getJavacTask().getElements();
    }
        
    /**
     * Returns {@link TreeUtilities}.
     * @return TreeUtilities
     */
    public synchronized TreeUtilities getTreeUtilities() {
        if (treeUtilities == null) {
            treeUtilities = new TreeUtilities(this);
        }
        return treeUtilities;
    }
    
    /**
     * Returns {@link ElementUtilities}.
     * @return ElementUtilities
     */
    public synchronized ElementUtilities getElementUtilities() {
        if (elementUtilities == null) {
            elementUtilities = new ElementUtilities(this);

        }
        return elementUtilities;
    }
    
    /**Get the TypeUtilities.
     * @return an instance of TypeUtilities
     */
    public synchronized TypeUtilities getTypeUtilities() {
        if (typeUtilities == null) {
            typeUtilities = new TypeUtilities(this);
        }
        return typeUtilities;
    }
    
    /**
     * Returns the {@link SourceVersion} used by the javac represented by this {@link CompilationInfo}.
     * @return SourceVersion
     * @since 0.47
     */
    public SourceVersion getSourceVersion() {
        return Source.toSourceVersion(Source.instance(getJavacTask().getContext()));
    }
//    /**
//     * Returns {@link ClasspathInfo} for which this {@link CompilationInfoImpl} was created.
//     * @return ClasspathInfo
//     */
//    public ClasspathInfo getClasspathInfo() {
//	return this.cpInfo;
//    }
//    
//    /**
//     * Returns {@link JavacParser} which created this {@link CompilationInfoImpl}
//     * or null when the {@link CompilationInfoImpl} was created for no files.
//     * @return {@link JavacParser} or null
//     */
//    public JavacParser getParser () {
//        return this.parser;
//    }
//    
//    /**
//     * Returns the {@link FileObject} represented by this {@link CompilationInfo}.
//     * @return FileObject
//     */
//    public FileObject getFileObject () {
//        return this.file;
//    }
//    
//    public FileObject getRoot () {
//        return this.root;
//    }
//    
//    public boolean isClassFile () {
//        return this.isClassFile;
//    }
//    
//    /**
//     * Returns {@link Document} of this {@link CompilationInfoImpl}
//     * @return Document or null when the {@link DataObject} doesn't
//     * exist or has no {@link EditorCookie}.
//     * @throws java.io.IOException
//     */
//    public Document getDocument() {        
//        if (this.file == null) {
//            return null;
//        }
//        if (!this.file.isValid()) {
//            return null;
//        }
//        try {
//            DataObject od = DataObject.find(file);            
//            EditorCookie ec = od.getCookie(EditorCookie.class);
//            if (ec != null) {
//                return  ec.getDocument();
//            } else {
//                return null;
//            }
//        } catch (DataObjectNotFoundException e) {
//            //may happen when the underlying FileObject has just been deleted
//            //should be safe to ignore
//            Logger.getLogger(CompilationInfoImpl.class.getName()).log(Level.FINE, null, e);
//            return null;
//        }
//    }
//        
                                
    /**
     * Moves the state to required phase. If given state was already reached 
     * the state is not changed. The method will throw exception if a state is 
     * illegal required. Acceptable parameters for thid method are <BR>
     * <LI>{@link org.netbeans.api.java.source.JavaSource.Phase.PARSED}
     * <LI>{@link org.netbeans.api.java.source.JavaSource.Phase.ELEMENTS_RESOLVED}
     * <LI>{@link org.netbeans.api.java.source.JavaSource.Phase.RESOLVED}
     * <LI>{@link org.netbeans.api.java.source.JavaSource.Phase.UP_TO_DATE}   
     * @param phase The required phase
     * @return the reached state
     * @throws IllegalArgumentException in case that given state can not be 
     *         reached using this method
     * @throws IOException when the file cannot be red
     */    
    public Phase toPhase(Phase phase) throws IOException {
        if (phase == Phase.MODIFIED) {
            throw new IllegalArgumentException( "Invalid phase: " + phase );    //NOI18N
        }
        if (!hasSource()) {
            Phase currentPhase = getPhase();
            if (currentPhase.compareTo(phase)<0) {
                setPhase(phase);
                if (currentPhase == Phase.MODIFIED)
                    getJavacTask().parse(); // Ensure proper javac initialization
                currentPhase = phase;
            }
            return currentPhase;
        }
        else {
            Phase currentPhase = moveToPhase(phase);
            return currentPhase.compareTo (phase) < 0 ? currentPhase : phase;
        }
    }

    /**
     * Returns {@link JavacTaskImpl}, when it doesn't exist
     * it's created.
     * @return JavacTaskImpl
     */
    public synchronized JavacTaskImpl getJavacTask() {
        if (javacTask == null) {
            javacTask = (JavacTaskImpl)JavacTool.create().getTask(null, this.jfm, diagnosticListener, Arrays.asList("-source", "1.7", "-target", "1.7"), null, Arrays.asList(this.jfo));
        }
	return javacTask;
    }

//    public Object getCachedValue(Object key) {
//        for (Map<Object, Object> c : userCache.values()) {
//            Object res = c.get(key);
//
//            if (res != null) return res;
//        }
//
//        return null;
//    }
//
//    public void putCachedValue(Object key, Object value, CacheClearPolicy clearPolicy) {
//        for (Map<Object, Object> c : userCache.values()) {
//            c.remove(key);
//        }
//
//        Map<Object, Object> c = userCache.get(clearPolicy);
//
//        if (c == null) {
//            userCache.put(clearPolicy, c = new HashMap<Object, Object>());
//        }
//
//        c.put(key, value);
//    }
//
//    public void taskFinished() {
//        userCache.remove(CacheClearPolicy.ON_TASK_END);
//    }
//
//    public void dispose() {
//        userCache.clear();
//    }
//    
    /**
     * Returns current {@link DiagnosticListener}
     * @return listener
     */
    DiagnosticListener<JavaFileObject> getDiagnosticListener() {
        return diagnosticListener;
    }
    
    /**
     * Sets the current {@link JavaSource.Phase}
     * @param phase
     */
    void setPhase(final Phase phase) {
        assert phase != null;
        if (phase == Phase.MODIFIED) {
            this.compilationUnit = null;
            this.diagnosticListener = new DiagnosticListenerImpl();
            this.javacTask = null;
            this.elementUtilities = null;
            this.treeUtilities = null;
            this.typeUtilities = null;
        }
        this.phase = phase;
    }
    
    /**
     * Sets the {@link CompilationUnitTree}
     * @param compilationUnit
     */
    void setCompilationUnit(final CompilationUnitTree compilationUnit) {
        assert compilationUnit != null;
        this.compilationUnit = compilationUnit;
    }
                
    private boolean hasSource () {
        return this.jfo != null && !isClassFile;
    }
    
    /**
     * Moves the Javac into the required {@link JavaSource#Phase}
     * Not synchronized, has to be called under Parsing API lock.
     * @param the required {@link JavaSource#Phase}
     * @parma currentInfo - the javac
     * @param cancellable when true the method checks cancels
     * @return the reached phase
     * @throws IOException when the javac throws an exception
     */
    private Phase moveToPhase (final Phase phase) throws IOException {
        Phase parserError = parserCrashed;
        assert parserError != null;
        Phase currentPhase = getPhase();
        try {
            if (currentPhase.compareTo(Phase.PARSED)<0 && phase.compareTo(Phase.PARSED)>=0 && phase.compareTo(parserError)<=0) {
                Iterable<? extends CompilationUnitTree> cuts = getJavacTask().parse();
                if (cuts == null) {
                    LOGGER.log( Level.INFO, "Did not parse anything for: {0}", jfo.toUri()); //NOI18N
                    return Phase.MODIFIED;
                }
                Iterator<? extends CompilationUnitTree> it = cuts.iterator();
                if (!it.hasNext()) {
                    LOGGER.log( Level.INFO, "Did not parse anything for: {0}", jfo.toUri()); //NOI18N
                    return Phase.MODIFIED;
                }
                CompilationUnitTree unit = it.next();
                setCompilationUnit(unit);
                assert !it.hasNext();
                currentPhase = Phase.PARSED;
            }
            if (currentPhase == Phase.PARSED && phase.compareTo(Phase.ELEMENTS_RESOLVED)>=0 && phase.compareTo(parserError)<=0) {
                getJavacTask().enter();
                currentPhase = Phase.ELEMENTS_RESOLVED;
            }
            if (currentPhase == Phase.ELEMENTS_RESOLVED && phase.compareTo(Phase.RESOLVED)>=0 && phase.compareTo(parserError)<=0) {
                getJavacTask().analyze();
                currentPhase = Phase.RESOLVED;
            }
            if ((currentPhase == Phase.RESOLVED || currentPhase == Phase.UP_TO_DATE) && phase.compareTo(Phase.GENERATED)==0 && phase.compareTo(parserError)<=0) {
                getJavacTask().generate();
                currentPhase = Phase.MODIFIED;
            }
            if (currentPhase == Phase.RESOLVED && phase.compareTo(Phase.UP_TO_DATE)>=0) {
                currentPhase = Phase.UP_TO_DATE;
            }
        } catch (Exception ex) {
            parserError = currentPhase;
            throw ex;
        } finally {
            setPhase(currentPhase);
            parserCrashed = parserError;
        }
        return currentPhase;
    }
    
    // Innerclasses ------------------------------------------------------------
    @Trusted
    private static class DiagnosticListenerImpl implements DiagnosticListener<JavaFileObject> {
        
        private final List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<>();
        
        @Override
        public void report(Diagnostic<? extends JavaFileObject> message) {
            errors.add(message);
        }

        private List<Diagnostic<? extends JavaFileObject>> getErrors(JavaFileObject file) {
            return errors;
        }
    }

    public static enum Phase {
        MODIFIED,
        PARSED,        
        ELEMENTS_RESOLVED,   
        RESOLVED,
        GENERATED,
        UP_TO_DATE
    };
    
    public static final class Pair<X, Y> {

        public final X first;
        public final Y second;

        public Pair(X first, Y second) {
            this.first = first;
            this.second = second;
        }
    }
}
