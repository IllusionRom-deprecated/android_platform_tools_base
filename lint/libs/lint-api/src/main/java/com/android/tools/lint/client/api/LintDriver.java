/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.client.api;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ATTR_IGNORE;
import static com.android.SdkConstants.CLASS_CONSTRUCTOR;
import static com.android.SdkConstants.CONSTRUCTOR_NAME;
import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FN_PROJECT_PROGUARD_FILE;
import static com.android.SdkConstants.OLD_PROGUARD_FILE;
import static com.android.SdkConstants.RES_FOLDER;
import static com.android.SdkConstants.SUPPRESS_ALL;
import static com.android.SdkConstants.SUPPRESS_LINT;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.lint.detector.api.LintUtils.isAnonymousClass;
import static org.objectweb.asm.Opcodes.ASM4;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.lint.client.api.LintListener.EventType;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.annotations.Beta;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.ast.Annotation;
import lombok.ast.AnnotationElement;
import lombok.ast.AnnotationValue;
import lombok.ast.ArrayInitializer;
import lombok.ast.ClassDeclaration;
import lombok.ast.ConstructorDeclaration;
import lombok.ast.Expression;
import lombok.ast.MethodDeclaration;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.StrictListAccessor;
import lombok.ast.StringLiteral;
import lombok.ast.TypeReference;
import lombok.ast.VariableDefinition;

/**
 * Analyzes Android projects and files
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class LintDriver {
    /**
     * Max number of passes to run through the lint runner if requested by
     * {@link #requestRepeat}
     */
    private static final int MAX_PHASES = 3;
    private static final String SUPPRESS_LINT_VMSIG = '/' + SUPPRESS_LINT + ';';

    private final LintClient mClient;
    private LintRequest mRequest;
    private IssueRegistry mRegistry;
    private volatile boolean mCanceled;
    private EnumSet<Scope> mScope;
    private List<? extends Detector> mApplicableDetectors;
    private Map<Scope, List<Detector>> mScopeDetectors;
    private List<LintListener> mListeners;
    private int mPhase;
    private List<Detector> mRepeatingDetectors;
    private EnumSet<Scope> mRepeatScope;
    private Project[] mCurrentProjects;
    private Project mCurrentProject;
    private boolean mAbbreviating = true;
    private boolean mParserErrors;
    private Map<Object,Object> mProperties;

    /**
     * Creates a new {@link LintDriver}
     *
     * @param registry The registry containing issues to be checked
     * @param client the tool wrapping the analyzer, such as an IDE or a CLI
     */
    public LintDriver(@NonNull IssueRegistry registry, @NonNull LintClient client) {
        mRegistry = registry;
        mClient = new LintClientWrapper(client);
    }

    /** Cancels the current lint run as soon as possible */
    public void cancel() {
        mCanceled = true;
    }

    /**
     * Returns the scope for the lint job
     *
     * @return the scope, never null
     */
    @NonNull
    public EnumSet<Scope> getScope() {
        return mScope;
    }

    /**
     * Returns the lint client requesting the lint check. This may not be the same
     * instance as the one passed in to this driver; lint uses a wrapper which performs
     * additional validation to ensure that for example badly behaved detectors which report
     * issues that have been disabled will get muted without the real lint client getting
     * notified. Thus, this {@link LintClient} is suitable for use by detectors to look
     * up a client to for example get location handles from, but tool handling code should
     * never try to cast this client back to their original lint client. For the original
     * lint client, use {@link LintRequest} instead.
     *
     * @return the client, never null
     */
    @NonNull
    public LintClient getClient() {
        return mClient;
    }

    /**
     * Returns the current request, which points to the original files to be checked,
     * the original scope, the original {@link LintClient}, as well as the release mode.
     *
     * @return the request
     */
    @NonNull
    public LintRequest getRequest() {
        return mRequest;
    }

    /**
     * Records a property for later retrieval by {@link #getProperty(Object)}
     *
     * @param key the key to associate the value with
     * @param value the value, or null to remove a previous binding
     */
    public void putProperty(@NonNull Object key, @Nullable Object value) {
        if (mProperties == null) {
            mProperties = Maps.newHashMap();
        }
        if (value == null) {
            mProperties.remove(key);
        } else {
            mProperties.put(key, value);
        }
    }

    /**
     * Returns the property previously stored with the given key, or null
     *
     * @param key the key
     * @return the value or null if not found
     */
    @Nullable
    public Object getProperty(@NonNull Object key) {
        if (mProperties != null) {
            return mProperties.get(key);
        }

        return null;
    }

    /**
     * Returns the current phase number. The first pass is numbered 1. Only one pass
     * will be performed, unless a {@link Detector} calls {@link #requestRepeat}.
     *
     * @return the current phase, usually 1
     */
    public int getPhase() {
        return mPhase;
    }

    /**
     * Returns the current {@link IssueRegistry}.
     *
     * @return the current {@link IssueRegistry}
     */
    @NonNull
    public IssueRegistry getRegistry() {
        return mRegistry;
    }

    /**
     * Returns the project containing a given file, or null if not found. This searches
     * only among the currently checked project and its library projects, not among all
     * possible projects being scanned sequentially.
     *
     * @param file the file to be checked
     * @return the corresponding project, or null if not found
     */
    @Nullable
    public Project findProjectFor(@NonNull File file) {
        if (mCurrentProjects != null) {
            if (mCurrentProjects.length == 1) {
                return mCurrentProjects[0];
            }
            String path = file.getPath();
            for (Project project : mCurrentProjects) {
                if (path.startsWith(project.getDir().getPath())) {
                    return project;
                }
            }
        }

        return null;
    }

    /**
     * Sets whether lint should abbreviate output when appropriate.
     *
     * @param abbreviating true to abbreviate output, false to include everything
     */
    public void setAbbreviating(boolean abbreviating) {
        mAbbreviating = abbreviating;
    }

    /**
     * Returns whether lint should abbreviate output when appropriate.
     *
     * @return true if lint should abbreviate output, false when including everything
     */
    public boolean isAbbreviating() {
        return mAbbreviating;
    }

    /**
     * Returns whether lint has encountered any files with fatal parser errors
     * (e.g. broken source code, or even broken parsers)
     * <p>
     * This is useful for checks that need to make sure they've seen all data in
     * order to be conclusive (such as an unused resource check).
     *
     * @return true if any files were not properly processed because they
     *         contained parser errors
     */
    public boolean hasParserErrors() {
        return mParserErrors;
    }

    /**
     * Sets whether lint has encountered files with fatal parser errors.
     *
     * @see #hasParserErrors()
     * @param hasErrors whether parser errors have been encountered
     */
    public void setHasParserErrors(boolean hasErrors) {
        mParserErrors = hasErrors;
    }

    /**
     * Returns the projects being analyzed
     *
     * @return the projects being analyzed
     */
    @NonNull
    public List<Project> getProjects() {
        if (mCurrentProjects != null) {
            return Arrays.asList(mCurrentProjects);
        }
        return Collections.emptyList();
    }

    /**
     * Analyze the given file (which can point to an Android project). Issues found
     * are reported to the associated {@link LintClient}.
     *
     * @param files the files and directories to be analyzed
     * @param scope the scope of the analysis; detectors with a wider scope will
     *            not be run. If null, the scope will be inferred from the files.
     * @deprecated use {@link #analyze(LintRequest) instead}
     */
    @Deprecated
    public void analyze(@NonNull List<File> files, @Nullable EnumSet<Scope> scope) {
        analyze(new LintRequest(mClient, files).setScope(scope));
    }

    /**
     * Analyze the given files (which can point to Android projects or directories
     * containing Android projects). Issues found are reported to the associated
     * {@link LintClient}.
     * <p>
     * Note that the {@link LintDriver} is not multi thread safe or re-entrant;
     * if you want to run potentially overlapping lint jobs, create a separate driver
     * for each job.
     *
     * @param request the files and directories to be analyzed
     */
    public void analyze(@NonNull LintRequest request) {
        try {
            mRequest = request;
            analyze();
        } finally {
            mRequest = null;
        }
    }

    /** Runs the driver to analyze the requested files */
    private void analyze() {
        mCanceled = false;
        mScope = mRequest.getScope();
        Collection<Project> projects;
        try {
            projects = mRequest.getProjects();
            if (projects == null) {
                projects = computeProjects(mRequest.getFiles());
            }
        } catch (CircularDependencyException e) {
            mCurrentProject = e.getProject();
            if (mCurrentProject != null) {
                File file = e.getLocation().getFile();
                Context context = new Context(this, mCurrentProject, null, file);
                context.report(IssueRegistry.LINT_ERROR, e.getLocation(), e.getMessage(), null);
                mCurrentProject = null;
            }
            return;
        }
        if (projects.isEmpty()) {
            mClient.log(null, "No projects found for %1$s", mRequest.getFiles().toString());
            return;
        }
        if (mCanceled) {
            return;
        }

        registerCustomRules(projects);

        if (mScope == null) {
            mScope = Scope.infer(projects);
        }

        fireEvent(EventType.STARTING, null);

        for (Project project : projects) {
            mPhase = 1;

            // The set of available detectors varies between projects
            computeDetectors(project);

            if (mApplicableDetectors.isEmpty()) {
                // No detectors enabled in this project: skip it
                continue;
            }

            checkProject(project);
            if (mCanceled) {
                break;
            }

            runExtraPhases(project);
        }

        fireEvent(mCanceled ? EventType.CANCELED : EventType.COMPLETED, null);
    }

    private void registerCustomRules(Collection<Project> projects) {
        // Look at the various projects, and if any of them provide a custom
        // lint jar, "add" them (this will replace the issue registry with
        // a CompositeIssueRegistry containing the original issue registry
        // plus JarFileIssueRegistry instances for each lint jar
        Set<File> jarFiles = Sets.newHashSet();
        for (Project project : projects) {
            jarFiles.addAll(mClient.findRuleJars(project));
        }

        jarFiles.addAll(mClient.findGlobalRuleJars());

        if (!jarFiles.isEmpty()) {
            List<IssueRegistry> registries = Lists.newArrayListWithExpectedSize(jarFiles.size());
            registries.add(mRegistry);
            for (File jarFile : jarFiles) {
                try {
                    registries.add(JarFileIssueRegistry.get(mClient, jarFile));
                } catch (Throwable e) {
                    mClient.log(e, "Could not load custom rule jar file %1$s", jarFile);
                }
            }
            if (registries.size() > 1) { // the first item is mRegistry itself
                mRegistry = new CompositeIssueRegistry(registries);
            }
        }
    }

    private void runExtraPhases(Project project) {
        // Did any detectors request another phase?
        if (mRepeatingDetectors != null) {
            // Yes. Iterate up to MAX_PHASES times.

            // During the extra phases, we might be narrowing the scope, and setting it in the
            // scope field such that detectors asking about the available scope will get the
            // correct result. However, we need to restore it to the original scope when this
            // is done in case there are other projects that will be checked after this, since
            // the repeated phases is done *per project*, not after all projects have been
            // processed.
            EnumSet<Scope> oldScope = mScope;

            do {
                mPhase++;
                fireEvent(EventType.NEW_PHASE,
                        new Context(this, project, null, project.getDir()));

                // Narrow the scope down to the set of scopes requested by
                // the rules.
                if (mRepeatScope == null) {
                    mRepeatScope = Scope.ALL;
                }
                mScope = Scope.intersect(mScope, mRepeatScope);
                if (mScope.isEmpty()) {
                    break;
                }

                // Compute the detectors to use for this pass.
                // Unlike the normal computeDetectors(project) call,
                // this is going to use the existing instances, and include
                // those that apply for the configuration.
                computeRepeatingDetectors(mRepeatingDetectors, project);

                if (mApplicableDetectors.isEmpty()) {
                    // No detectors enabled in this project: skip it
                    continue;
                }

                checkProject(project);
                if (mCanceled) {
                    break;
                }
            } while (mPhase < MAX_PHASES && mRepeatingDetectors != null);

            mScope = oldScope;
        }
    }

    private void computeRepeatingDetectors(List<Detector> detectors, Project project) {
        // Ensure that the current visitor is recomputed
        mCurrentFolderType = null;
        mCurrentVisitor = null;

        // Create map from detector class to issue such that we can
        // compute applicable issues for each detector in the list of detectors
        // to be repeated
        List<Issue> issues = mRegistry.getIssues();
        Multimap<Class<? extends Detector>, Issue> issueMap =
                ArrayListMultimap.create(issues.size(), 3);
        for (Issue issue : issues) {
            issueMap.put(issue.getImplementation().getDetectorClass(), issue);
        }

        Map<Class<? extends Detector>, EnumSet<Scope>> detectorToScope =
                new HashMap<Class<? extends Detector>, EnumSet<Scope>>();
        Map<Scope, List<Detector>> scopeToDetectors =
                new EnumMap<Scope, List<Detector>>(Scope.class);

        List<Detector> detectorList = new ArrayList<Detector>();
        // Compute the list of detectors (narrowed down from mRepeatingDetectors),
        // and simultaneously build up the detectorToScope map which tracks
        // the scopes each detector is affected by (this is used to populate
        // the mScopeDetectors map which is used during iteration).
        Configuration configuration = project.getConfiguration();
        for (Detector detector : detectors) {
            Class<? extends Detector> detectorClass = detector.getClass();
            Collection<Issue> detectorIssues = issueMap.get(detectorClass);
            if (detectorIssues != null) {
                boolean add = false;
                for (Issue issue : detectorIssues) {
                    // The reason we have to check whether the detector is enabled
                    // is that this is a per-project property, so when running lint in multiple
                    // projects, a detector enabled only in a different project could have
                    // requested another phase, and we end up in this project checking whether
                    // the detector is enabled here.
                    if (!configuration.isEnabled(issue)) {
                        continue;
                    }

                    add = true; // Include detector if any of its issues are enabled

                    EnumSet<Scope> s = detectorToScope.get(detectorClass);
                    EnumSet<Scope> issueScope = issue.getImplementation().getScope();
                    if (s == null) {
                        detectorToScope.put(detectorClass, issueScope);
                    } else if (!s.containsAll(issueScope)) {
                        EnumSet<Scope> union = EnumSet.copyOf(s);
                        union.addAll(issueScope);
                        detectorToScope.put(detectorClass, union);
                    }
                }

                if (add) {
                    detectorList.add(detector);
                    EnumSet<Scope> union = detectorToScope.get(detector.getClass());
                    for (Scope s : union) {
                        List<Detector> list = scopeToDetectors.get(s);
                        if (list == null) {
                            list = new ArrayList<Detector>();
                            scopeToDetectors.put(s, list);
                        }
                        list.add(detector);
                    }
                }
            }
        }

        mApplicableDetectors = detectorList;
        mScopeDetectors = scopeToDetectors;
        mRepeatingDetectors = null;
        mRepeatScope = null;

        validateScopeList();
    }

    private void computeDetectors(@NonNull Project project) {
        // Ensure that the current visitor is recomputed
        mCurrentFolderType = null;
        mCurrentVisitor = null;

        Configuration configuration = project.getConfiguration();
        mScopeDetectors = new EnumMap<Scope, List<Detector>>(Scope.class);
        mApplicableDetectors = mRegistry.createDetectors(mClient, configuration,
                mScope, mScopeDetectors);

        validateScopeList();
    }

    /** Development diagnostics only, run with assertions on */
    @SuppressWarnings("all") // Turn off warnings for the intentional assertion side effect below
    private void validateScopeList() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true; // Intentional side-effect
        if (assertionsEnabled) {
            List<Detector> resourceFileDetectors = mScopeDetectors.get(Scope.RESOURCE_FILE);
            if (resourceFileDetectors != null) {
                for (Detector detector : resourceFileDetectors) {
                    assert detector instanceof ResourceXmlDetector : detector;
                }
            }

            List<Detector> manifestDetectors = mScopeDetectors.get(Scope.MANIFEST);
            if (manifestDetectors != null) {
                for (Detector detector : manifestDetectors) {
                    assert detector instanceof Detector.XmlScanner : detector;
                }
            }
            List<Detector> javaCodeDetectors = mScopeDetectors.get(Scope.ALL_JAVA_FILES);
            if (javaCodeDetectors != null) {
                for (Detector detector : javaCodeDetectors) {
                    assert detector instanceof Detector.JavaScanner : detector;
                }
            }
            List<Detector> javaFileDetectors = mScopeDetectors.get(Scope.JAVA_FILE);
            if (javaFileDetectors != null) {
                for (Detector detector : javaFileDetectors) {
                    assert detector instanceof Detector.JavaScanner : detector;
                }
            }

            List<Detector> classDetectors = mScopeDetectors.get(Scope.CLASS_FILE);
            if (classDetectors != null) {
                for (Detector detector : classDetectors) {
                    assert detector instanceof Detector.ClassScanner : detector;
                }
            }

            List<Detector> classCodeDetectors = mScopeDetectors.get(Scope.ALL_CLASS_FILES);
            if (classCodeDetectors != null) {
                for (Detector detector : classCodeDetectors) {
                    assert detector instanceof Detector.ClassScanner : detector;
                }
            }

            List<Detector> otherDetectors = mScopeDetectors.get(Scope.OTHER);
            if (otherDetectors != null) {
                for (Detector detector : otherDetectors) {
                    assert detector instanceof Detector.OtherFileScanner : detector;
                }
            }
        }
    }

    private void registerProjectFile(
            @NonNull Map<File, Project> fileToProject,
            @NonNull File file,
            @NonNull File projectDir,
            @NonNull File rootDir) {
        fileToProject.put(file, mClient.getProject(projectDir, rootDir));
    }

    private Collection<Project> computeProjects(@NonNull List<File> files) {
        // Compute list of projects
        Map<File, Project> fileToProject = new HashMap<File, Project>();

        File sharedRoot = null;

        // Ensure that we have absolute paths such that if you lint
        //  "foo bar" in "baz" we can show baz/ as the root
        if (files.size() > 1) {
            List<File> absolute = new ArrayList<File>(files.size());
            for (File file : files) {
                absolute.add(file.getAbsoluteFile());
            }
            files = absolute;

            sharedRoot = LintUtils.getCommonParent(files);
            if (sharedRoot != null && sharedRoot.getParentFile() == null) { // "/" ?
                sharedRoot = null;
            }
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File rootDir = sharedRoot;
                if (rootDir == null) {
                    rootDir = file;
                    if (files.size() > 1) {
                        rootDir = file.getParentFile();
                        if (rootDir == null) {
                            rootDir = file;
                        }
                    }
                }

                // Figure out what to do with a directory. Note that the meaning of the
                // directory can be ambiguous:
                // If you pass a directory which is unknown, we don't know if we should
                // search upwards (in case you're pointing at a deep java package folder
                // within the project), or if you're pointing at some top level directory
                // containing lots of projects you want to scan. We attempt to do the
                // right thing, which is to see if you're pointing right at a project or
                // right within it (say at the src/ or res/) folder, and if not, you're
                // hopefully pointing at a project tree that you want to scan recursively.
                if (mClient.isProjectDirectory(file)) {
                    registerProjectFile(fileToProject, file, file, rootDir);
                    continue;
                } else {
                    File parent = file.getParentFile();
                    if (parent != null) {
                        if (mClient.isProjectDirectory(parent)) {
                            registerProjectFile(fileToProject, file, parent, parent);
                            continue;
                        } else {
                            parent = parent.getParentFile();
                            if (parent != null && mClient.isProjectDirectory(parent)) {
                                registerProjectFile(fileToProject, file, parent, parent);
                                continue;
                            }
                        }
                    }

                    // Search downwards for nested projects
                    addProjects(file, fileToProject, rootDir);
                }
            } else {
                // Pointed at a file: Search upwards for the containing project
                File parent = file.getParentFile();
                while (parent != null) {
                    if (mClient.isProjectDirectory(parent)) {
                        registerProjectFile(fileToProject, file, parent, parent);
                        break;
                    }
                    parent = parent.getParentFile();
                }
            }

            if (mCanceled) {
                return Collections.emptySet();
            }
        }

        for (Map.Entry<File, Project> entry : fileToProject.entrySet()) {
            File file = entry.getKey();
            Project project = entry.getValue();
            if (!file.equals(project.getDir())) {
                if (file.isDirectory()) {
                    try {
                        File dir = file.getCanonicalFile();
                        if (dir.equals(project.getDir())) {
                            continue;
                        }
                    } catch (IOException ioe) {
                        // pass
                    }
                }

                project.addFile(file);
            }
        }

        // Partition the projects up such that we only return projects that aren't
        // included by other projects (e.g. because they are library projects)

        Collection<Project> allProjects = fileToProject.values();
        Set<Project> roots = new HashSet<Project>(allProjects);
        for (Project project : allProjects) {
            roots.removeAll(project.getAllLibraries());
        }

        // Report issues for all projects that are explicitly referenced. We need to
        // do this here, since the project initialization will mark all library
        // projects as no-report projects by default.
        for (Project project : allProjects) {
            // Report issues for all projects explicitly listed or found via a directory
            // traversal -- including library projects.
            project.setReportIssues(true);
        }

        if (LintUtils.assertionsEnabled()) {
            // Make sure that all the project directories are unique. This ensures
            // that we didn't accidentally end up with different project instances
            // for a library project discovered as a directory as well as one
            // initialized from the library project dependency list
            IdentityHashMap<Project, Project> projects =
                    new IdentityHashMap<Project, Project>();
            for (Project project : roots) {
                projects.put(project, project);
                for (Project library : project.getAllLibraries()) {
                    projects.put(library, library);
                }
            }
            Set<File> dirs = new HashSet<File>();
            for (Project project : projects.keySet()) {
                assert !dirs.contains(project.getDir());
                dirs.add(project.getDir());
            }
        }

        return roots;
    }

    private void addProjects(
            @NonNull File dir,
            @NonNull Map<File, Project> fileToProject,
            @NonNull File rootDir) {
        if (mCanceled) {
            return;
        }

        if (mClient.isProjectDirectory(dir)) {
            registerProjectFile(fileToProject, dir, dir, rootDir);
        } else {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        addProjects(file, fileToProject, rootDir);
                    }
                }
            }
        }
    }

    private void checkProject(@NonNull Project project) {
        File projectDir = project.getDir();

        Context projectContext = new Context(this, project, null, projectDir);
        fireEvent(EventType.SCANNING_PROJECT, projectContext);

        List<Project> allLibraries = project.getAllLibraries();
        Set<Project> allProjects = new HashSet<Project>(allLibraries.size() + 1);
        allProjects.add(project);
        allProjects.addAll(allLibraries);
        mCurrentProjects = allProjects.toArray(new Project[allProjects.size()]);

        mCurrentProject = project;

        for (Detector check : mApplicableDetectors) {
            check.beforeCheckProject(projectContext);
            if (mCanceled) {
                return;
            }
        }

        assert mCurrentProject == project;
        runFileDetectors(project, project);

        if (!Scope.checkSingleFile(mScope)) {
            List<Project> libraries = project.getDirectLibraries();
            for (Project library : libraries) {
                Context libraryContext = new Context(this, library, project, projectDir);
                fireEvent(EventType.SCANNING_LIBRARY_PROJECT, libraryContext);
                mCurrentProject = library;

                for (Detector check : mApplicableDetectors) {
                    check.beforeCheckLibraryProject(libraryContext);
                    if (mCanceled) {
                        return;
                    }
                }
                assert mCurrentProject == library;

                runFileDetectors(library, project);
                if (mCanceled) {
                    return;
                }

                assert mCurrentProject == library;

                for (Detector check : mApplicableDetectors) {
                    check.afterCheckLibraryProject(libraryContext);
                    if (mCanceled) {
                        return;
                    }
                }
            }
        }

        mCurrentProject = project;

        for (Detector check : mApplicableDetectors) {
            check.afterCheckProject(projectContext);
            if (mCanceled) {
                return;
            }
        }

        if (mCanceled) {
            mClient.report(
                projectContext,
                    // Must provide an issue since API guarantees that the issue parameter
                IssueRegistry.CANCELLED,
                Severity.INFORMATIONAL,
                null /*range*/,
                "Lint canceled by user", null);
        }

        mCurrentProjects = null;
    }

    private void runFileDetectors(@NonNull Project project, @Nullable Project main) {
        // Look up manifest information (but not for library projects)
        for (File manifestFile : project.getManifestFiles()) {
            XmlContext context = new XmlContext(this, project, main, manifestFile, null);
            IDomParser parser = mClient.getDomParser();
            if (parser != null) {
                context.document = parser.parseXml(context);
                if (context.document != null) {
                    try {
                        project.readManifest(context.document);

                        if ((!project.isLibrary() || (main != null && main.isMergingManifests()))
                                && mScope.contains(Scope.MANIFEST)) {
                            List<Detector> detectors = mScopeDetectors.get(Scope.MANIFEST);
                            if (detectors != null) {
                                XmlVisitor v = new XmlVisitor(parser, detectors);
                                fireEvent(EventType.SCANNING_FILE, context);
                                v.visitFile(context, manifestFile);
                            }
                        }
                    } finally {
                      if (context.document != null) { // else: freed by XmlVisitor above
                          parser.dispose(context, context.document);
                      }
                    }
                }
            }
        }

        // Process both Scope.RESOURCE_FILE and Scope.ALL_RESOURCE_FILES detectors together
        // in a single pass through the resource directories.
        if (mScope.contains(Scope.ALL_RESOURCE_FILES) || mScope.contains(Scope.RESOURCE_FILE)) {
            List<Detector> checks = union(mScopeDetectors.get(Scope.RESOURCE_FILE),
                    mScopeDetectors.get(Scope.ALL_RESOURCE_FILES));
            if (checks != null && !checks.isEmpty()) {
                List<ResourceXmlDetector> xmlDetectors =
                        new ArrayList<ResourceXmlDetector>(checks.size());
                for (Detector detector : checks) {
                    if (detector instanceof ResourceXmlDetector) {
                        xmlDetectors.add((ResourceXmlDetector) detector);
                    }
                }
                if (!xmlDetectors.isEmpty()) {
                    List<File> files = project.getSubset();
                    if (files != null) {
                        checkIndividualResources(project, main, xmlDetectors, files);
                    } else {
                        List<File> resourceFolders = project.getResourceFolders();
                        if (!resourceFolders.isEmpty() && !xmlDetectors.isEmpty()) {
                            for (File res : resourceFolders) {
                                checkResFolder(project, main, res, xmlDetectors);
                            }
                        }
                    }
                }
            }
        }

        if (mCanceled) {
            return;
        }

        if (mScope.contains(Scope.JAVA_FILE) || mScope.contains(Scope.ALL_JAVA_FILES)) {
            List<Detector> checks = union(mScopeDetectors.get(Scope.JAVA_FILE),
                    mScopeDetectors.get(Scope.ALL_JAVA_FILES));
            if (checks != null && !checks.isEmpty()) {
                List<File> files = project.getSubset();
                if (files != null) {
                    checkIndividualJavaFiles(project, main, checks, files);
                } else {
                    List<File> sourceFolders = project.getJavaSourceFolders();
                    checkJava(project, main, sourceFolders, checks);
                }
            }
        }

        if (mCanceled) {
            return;
        }

        if (mScope.contains(Scope.CLASS_FILE)
                || mScope.contains(Scope.ALL_CLASS_FILES)
                || mScope.contains(Scope.JAVA_LIBRARIES)) {
            checkClasses(project, main);
        }

        if (mScope.contains(Scope.OTHER)) {
            List<Detector> checks = mScopeDetectors.get(Scope.OTHER);
            if (checks != null) {
                OtherFileVisitor visitor = new OtherFileVisitor(checks);
                visitor.scan(this, project, main);
            }
        }

        if (mCanceled) {
            return;
        }

        if (project == main && mScope.contains(Scope.PROGUARD_FILE)) {
            checkProGuard(project, main);
        }
    }

    private void checkProGuard(Project project, Project main) {
        List<Detector> detectors = mScopeDetectors.get(Scope.PROGUARD_FILE);
        if (detectors != null) {
            List<File> files = project.getProguardFiles();
            for (File file : files) {
                Context context = new Context(this, project, main, file);
                fireEvent(EventType.SCANNING_FILE, context);
                for (Detector detector : detectors) {
                    if (detector.appliesTo(context, file)) {
                        detector.beforeCheckFile(context);
                        detector.run(context);
                        detector.afterCheckFile(context);
                    }
                }
            }
        }
    }

    /** True if execution has been canceled */
    boolean isCanceled() {
        return mCanceled;
    }

    /**
     * Map from VM class name to corresponding super class VM name, if available.
     * This map is typically null except <b>during</b> class processing.
     */
    private Map<String, String> mSuperClassMap;

    /**
     * Returns the super class for the given class name,
     * which should be in VM format (e.g. java/lang/Integer, not java.lang.Integer).
     * If the super class is not known, returns null. This can happen if
     * the given class is not a known class according to the project or its
     * libraries, for example because it refers to one of the core libraries which
     * are not analyzed by lint.
     *
     * @param name the fully qualified class name
     * @return the corresponding super class name (in VM format), or null if not known
     */
    @Nullable
    public String getSuperClass(@NonNull String name) {
        if (mSuperClassMap == null) {
            throw new IllegalStateException("Only callable during ClassScanner#checkClass");
        }
        assert name.indexOf('.') == -1 : "Use VM signatures, e.g. java/lang/Integer";

        String superClass = mSuperClassMap.get(name);
        if (superClass == null && mCurrentProject != null) {
            if ("java/lang/Object".equals(name)) {  //$NON-NLS-1$
                return null;
            }
            superClass = mClient.getSuperClass(mCurrentProject, name);
            if (superClass != null) {
                mSuperClassMap.put(name, superClass);
            }
        }

        return superClass;
    }

    /**
     * Returns true if the given class is a subclass of the given super class.
     *
     * @param classNode the class to check whether it is a subclass of the given
     *            super class name
     * @param superClassName the fully qualified super class name (in VM format,
     *            e.g. java/lang/Integer, not java.lang.Integer.
     * @return true if the given class is a subclass of the given super class
     */
    public boolean isSubclassOf(@NonNull ClassNode classNode, @NonNull String superClassName) {
        if (superClassName.equals(classNode.superName)) {
            return true;
        }

        if (mCurrentProject != null) {
            Boolean isSub = mClient.isSubclassOf(mCurrentProject, classNode.name, superClassName);
            if (isSub != null) {
                return isSub.booleanValue();
            }
        }

        String className = classNode.name;
        while (className != null) {
            if (className.equals(superClassName)) {
                return true;
            }
            className = getSuperClass(className);
        }

        return false;
    }
    @Nullable
    private static List<Detector> union(
            @Nullable List<Detector> list1,
            @Nullable List<Detector> list2) {
        if (list1 == null) {
            return list2;
        } else if (list2 == null) {
            return list1;
        } else {
            // Use set to pick out unique detectors, since it's possible for there to be overlap,
            // e.g. the DuplicateIdDetector registers both a cross-resource issue and a
            // single-file issue, so it shows up on both scope lists:
            Set<Detector> set = new HashSet<Detector>(list1.size() + list2.size());
            if (list1 != null) {
                set.addAll(list1);
            }
            if (list2 != null) {
                set.addAll(list2);
            }

            return new ArrayList<Detector>(set);
        }
    }

    /** Check the classes in this project (and if applicable, in any library projects */
    private void checkClasses(Project project, Project main) {
        List<File> files = project.getSubset();
        if (files != null) {
            checkIndividualClassFiles(project, main, files);
            return;
        }

        // We need to read in all the classes up front such that we can initialize
        // the parent chains (such that for example for a virtual dispatch, we can
        // also check the super classes).

        List<File> libraries = project.getJavaLibraries();
        List<ClassEntry> libraryEntries;
        if (!libraries.isEmpty()) {
            libraryEntries = new ArrayList<ClassEntry>(64);
            findClasses(libraryEntries, libraries);
            Collections.sort(libraryEntries);
        } else {
            libraryEntries = Collections.emptyList();
        }

        List<File> classFolders = project.getJavaClassFolders();
        List<ClassEntry> classEntries;
        if (classFolders.isEmpty()) {
            String message = String.format("No .class files were found in project \"%1$s\", "
                    + "so none of the classfile based checks could be run. "
                    + "Does the project need to be built first?", project.getName());
            Location location = Location.create(project.getDir());
            mClient.report(new Context(this, project, main, project.getDir()),
                    IssueRegistry.LINT_ERROR,
                    project.getConfiguration().getSeverity(IssueRegistry.LINT_ERROR),
                    location, message, null);
            classEntries = Collections.emptyList();
        } else {
            classEntries = new ArrayList<ClassEntry>(64);
            findClasses(classEntries, classFolders);
            Collections.sort(classEntries);
        }

        if (getPhase() == 1) {
            mSuperClassMap = getSuperMap(libraryEntries, classEntries);
        }

        // Actually run the detectors. Libraries should be called before the
        // main classes.
        runClassDetectors(Scope.JAVA_LIBRARIES, libraryEntries, project, main);

        if (mCanceled) {
            return;
        }

        runClassDetectors(Scope.CLASS_FILE, classEntries, project, main);
        runClassDetectors(Scope.ALL_CLASS_FILES, classEntries, project, main);
    }

    private void checkIndividualClassFiles(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<File> files) {
        List<ClassEntry> entries = new ArrayList<ClassEntry>(files.size());

        List<File> classFolders = project.getJavaClassFolders();
        if (!classFolders.isEmpty()) {
            for (File file : files) {
                String path = file.getPath();
                if (file.isFile() && path.endsWith(DOT_CLASS)) {
                    try {
                        byte[] bytes = mClient.readBytes(file);
                        if (bytes != null) {
                            for (File dir : classFolders) {
                                if (path.startsWith(dir.getPath())) {
                                    entries.add(new ClassEntry(file, null /* jarFile*/, dir,
                                            bytes));
                                    break;
                                }
                            }
                        }
                    } catch (IOException e) {
                        mClient.log(e, null);
                        continue;
                    }

                    if (mCanceled) {
                        return;
                    }
                }
            }

            if (!entries.isEmpty()) {
                Collections.sort(entries);
                // No superclass info available on individual lint runs, unless
                // the client can provide it
                mSuperClassMap = Maps.newHashMap();
                runClassDetectors(Scope.CLASS_FILE, entries, project, main);
            }
        }
    }

    /**
     * Stack of {@link ClassNode} nodes for outer classes of the currently
     * processed class, including that class itself. Populated by
     * {@link #runClassDetectors(Scope, List, Project, Project)} and used by
     * {@link #getOuterClassNode(ClassNode)}
     */
    private Deque<ClassNode> mOuterClasses;

    private void runClassDetectors(Scope scope, List<ClassEntry> entries,
            Project project, Project main) {
        if (mScope.contains(scope)) {
            List<Detector> classDetectors = mScopeDetectors.get(scope);
            if (classDetectors != null && !classDetectors.isEmpty() && !entries.isEmpty()) {
                AsmVisitor visitor = new AsmVisitor(mClient, classDetectors);

                String sourceContents = null;
                String sourceName = "";
                mOuterClasses = new ArrayDeque<ClassNode>();
                for (ClassEntry entry : entries) {
                    ClassReader reader;
                    ClassNode classNode;
                    try {
                        reader = new ClassReader(entry.bytes);
                        classNode = new ClassNode();
                        reader.accept(classNode, 0 /* flags */);
                    } catch (Throwable t) {
                        mClient.log(null, "Error processing %1$s: broken class file?",
                                entry.path());
                        continue;
                    }

                    ClassNode peek;
                    while ((peek = mOuterClasses.peek()) != null) {
                        if (classNode.name.startsWith(peek.name)) {
                            break;
                        } else {
                            mOuterClasses.pop();
                        }
                    }
                    mOuterClasses.push(classNode);

                    if (isSuppressed(null, classNode)) {
                        // Class was annotated with suppress all -- no need to look any further
                        continue;
                    }

                    if (sourceContents != null) {
                        // Attempt to reuse the source buffer if initialized
                        // This means making sure that the source files
                        //    foo/bar/MyClass and foo/bar/MyClass$Bar
                        //    and foo/bar/MyClass$3 and foo/bar/MyClass$3$1 have the same prefix.
                        String newName = classNode.name;
                        int newRootLength = newName.indexOf('$');
                        if (newRootLength == -1) {
                            newRootLength = newName.length();
                        }
                        int oldRootLength = sourceName.indexOf('$');
                        if (oldRootLength == -1) {
                            oldRootLength = sourceName.length();
                        }
                        if (newRootLength != oldRootLength ||
                                !sourceName.regionMatches(0, newName, 0, newRootLength)) {
                            sourceContents = null;
                        }
                    }

                    ClassContext context = new ClassContext(this, project, main,
                            entry.file, entry.jarFile, entry.binDir, entry.bytes,
                            classNode, scope == Scope.JAVA_LIBRARIES /*fromLibrary*/,
                            sourceContents);

                    try {
                        visitor.runClassDetectors(context);
                    } catch (Exception e) {
                        mClient.log(e, null);
                    }

                    if (mCanceled) {
                        return;
                    }

                    sourceContents = context.getSourceContents(false/*read*/);
                    sourceName = classNode.name;
                }

                mOuterClasses = null;
            }
        }
    }

    /** Returns the outer class node of the given class node
     * @param classNode the inner class node
     * @return the outer class node */
    public ClassNode getOuterClassNode(@NonNull ClassNode classNode) {
        String outerName = classNode.outerClass;

        Iterator<ClassNode> iterator = mOuterClasses.iterator();
        while (iterator.hasNext()) {
            ClassNode node = iterator.next();
            if (outerName != null) {
                if (node.name.equals(outerName)) {
                    return node;
                }
            } else if (node == classNode) {
                return iterator.hasNext() ? iterator.next() : null;
            }
        }

        return null;
    }

    private Map<String, String> getSuperMap(List<ClassEntry> libraryEntries,
            List<ClassEntry> classEntries) {
        int size = libraryEntries.size() + classEntries.size();
        Map<String, String> map = new HashMap<String, String>(size);

        SuperclassVisitor visitor = new SuperclassVisitor(map);
        addSuperClasses(visitor, libraryEntries);
        addSuperClasses(visitor, classEntries);

        return map;
    }

    private void addSuperClasses(SuperclassVisitor visitor, List<ClassEntry> entries) {
        for (ClassEntry entry : entries) {
            try {
                ClassReader reader = new ClassReader(entry.bytes);
                int flags = ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES;
                reader.accept(visitor, flags);
            } catch (Throwable t) {
                mClient.log(null, "Error processing %1$s: broken class file?", entry.path());
            }
        }
    }

    /**
     * Returns the {@link ClassNode} corresponding to the given type, if possible, or null
     *
     * @param type the fully qualified type, using JVM signatures (/ and $, not . as path
     *             separators)
     * @param flags the ASM flags to pass to the {@link ClassReader}, normally 0 but can
     *              for example be {@link ClassReader#SKIP_CODE} and/oor
     *              {@link ClassReader#SKIP_DEBUG}
     * @return the class node for the type, or null
     */
    @Nullable
    public ClassNode findClass(@NonNull ClassContext context, @NonNull String type, int flags) {
        String relative = type.replace('/', File.separatorChar) + DOT_CLASS;
        File classFile = findClassFile(context.getProject(), relative);
        if (classFile != null) {
            if (classFile.getPath().endsWith(DOT_JAR)) {
                // TODO: Handle .jar files
                return null;
            }

            try {
                byte[] bytes = mClient.readBytes(classFile);
                ClassReader reader = new ClassReader(bytes);
                ClassNode classNode = new ClassNode();
                reader.accept(classNode, flags);

                return classNode;
            } catch (Throwable t) {
                mClient.log(null, "Error processing %1$s: broken class file?",
                        classFile.getPath());
            }
        }

        return null;
    }

    @Nullable
    private File findClassFile(@NonNull Project project, String relativePath) {
        for (File root : mClient.getJavaClassFolders(project)) {
            File path = new File(root, relativePath);
            if (path.exists()) {
                return path;
            }
        }
        // Search in the libraries
        for (File root : mClient.getJavaLibraries(project)) {
            // TODO: Handle .jar files!
            //if (root.getPath().endsWith(DOT_JAR)) {
            //}

            File path = new File(root, relativePath);
            if (path.exists()) {
                return path;
            }
        }

        // Search dependent projects
        for (Project library : project.getDirectLibraries()) {
            File path = findClassFile(library, relativePath);
            if (path != null) {
                return path;
            }
        }

        return null;
    }

    /** Visitor skimming classes and initializing a map of super classes */
    private static class SuperclassVisitor extends ClassVisitor {
        private final Map<String, String> mMap;

        public SuperclassVisitor(Map<String, String> map) {
            super(ASM4);
            mMap = map;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            if (superName != null) {
                mMap.put(name, superName);
            }
        }
    }

    private void findClasses(
            @NonNull List<ClassEntry> entries,
            @NonNull List<File> classPath) {
        for (File classPathEntry : classPath) {
            if (classPathEntry.getName().endsWith(DOT_JAR)) {
                File jarFile = classPathEntry;
                if (!jarFile.exists()) {
                    continue;
                }
                ZipInputStream zis = null;
                try {
                    FileInputStream fis = new FileInputStream(jarFile);
                    zis = new ZipInputStream(fis);
                    ZipEntry entry = zis.getNextEntry();
                    while (entry != null) {
                        String name = entry.getName();
                        if (name.endsWith(DOT_CLASS)) {
                            try {
                                byte[] bytes = ByteStreams.toByteArray(zis);
                                if (bytes != null) {
                                    File file = new File(entry.getName());
                                    entries.add(new ClassEntry(file, jarFile, jarFile, bytes));
                                }
                            } catch (Exception e) {
                                mClient.log(e, null);
                                continue;
                            }
                        }

                        if (mCanceled) {
                            return;
                        }

                        entry = zis.getNextEntry();
                    }
                } catch (IOException e) {
                    mClient.log(e, "Could not read jar file contents from %1$s", jarFile);
                } finally {
                    Closeables.closeQuietly(zis);
                }

                continue;
            } else if (classPathEntry.isDirectory()) {
                File binDir = classPathEntry;
                List<File> classFiles = new ArrayList<File>();
                addClassFiles(binDir, classFiles);

                for (File file : classFiles) {
                    try {
                        byte[] bytes = mClient.readBytes(file);
                        if (bytes != null) {
                            entries.add(new ClassEntry(file, null /* jarFile*/, binDir, bytes));
                        }
                    } catch (IOException e) {
                        mClient.log(e, null);
                        continue;
                    }

                    if (mCanceled) {
                        return;
                    }
                }
            } else {
                mClient.log(null, "Ignoring class path entry %1$s", classPathEntry);
            }
        }
    }

    private static void addClassFiles(@NonNull File dir, @NonNull List<File> classFiles) {
        // Process the resource folder
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(DOT_CLASS)) {
                    classFiles.add(file);
                } else if (file.isDirectory()) {
                    // Recurse
                    addClassFiles(file, classFiles);
                }
            }
        }
    }

    private void checkJava(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<File> sourceFolders,
            @NonNull List<Detector> checks) {
        IJavaParser javaParser = mClient.getJavaParser();
        if (javaParser == null) {
            mClient.log(null, "No java parser provided to lint: not running Java checks");
            return;
        }

        assert !checks.isEmpty();

        // Gather all Java source files in a single pass; more efficient.
        List<File> sources = new ArrayList<File>(100);
        for (File folder : sourceFolders) {
            gatherJavaFiles(folder, sources);
        }
        if (!sources.isEmpty()) {
            JavaVisitor visitor = new JavaVisitor(javaParser, checks);
            for (File file : sources) {
                JavaContext context = new JavaContext(this, project, main, file);
                fireEvent(EventType.SCANNING_FILE, context);
                visitor.visitFile(context, file);
                if (mCanceled) {
                    return;
                }
            }
        }
    }

    private void checkIndividualJavaFiles(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<Detector> checks,
            @NonNull List<File> files) {

        IJavaParser javaParser = mClient.getJavaParser();
        if (javaParser == null) {
            mClient.log(null, "No java parser provided to lint: not running Java checks");
            return;
        }

        JavaVisitor visitor = new JavaVisitor(javaParser, checks);

        for (File file : files) {
            if (file.isFile() && file.getPath().endsWith(DOT_JAVA)) {
                JavaContext context = new JavaContext(this, project, main, file);
                fireEvent(EventType.SCANNING_FILE, context);
                visitor.visitFile(context, file);
                if (mCanceled) {
                    return;
                }
            }
        }
    }

    private static void gatherJavaFiles(@NonNull File dir, @NonNull List<File> result) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".java")) { //$NON-NLS-1$
                    result.add(file);
                } else if (file.isDirectory()) {
                    gatherJavaFiles(file, result);
                }
            }
        }
    }

    private ResourceFolderType mCurrentFolderType;
    private List<ResourceXmlDetector> mCurrentXmlDetectors;
    private XmlVisitor mCurrentVisitor;

    @Nullable
    private XmlVisitor getVisitor(
            @NonNull ResourceFolderType type,
            @NonNull List<ResourceXmlDetector> checks) {
        if (type != mCurrentFolderType) {
            mCurrentFolderType = type;

            // Determine which XML resource detectors apply to the given folder type
            List<ResourceXmlDetector> applicableChecks =
                    new ArrayList<ResourceXmlDetector>(checks.size());
            for (ResourceXmlDetector check : checks) {
                if (check.appliesTo(type)) {
                    applicableChecks.add(check);
                }
            }

            // If the list of detectors hasn't changed, then just use the current visitor!
            if (mCurrentXmlDetectors != null && mCurrentXmlDetectors.equals(applicableChecks)) {
                return mCurrentVisitor;
            }

            if (applicableChecks.isEmpty()) {
                mCurrentVisitor = null;
                return null;
            }

            IDomParser parser = mClient.getDomParser();
            if (parser != null) {
                mCurrentVisitor = new XmlVisitor(parser, applicableChecks);
            }
        }

        return mCurrentVisitor;
    }

    private void checkResFolder(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File res,
            @NonNull List<ResourceXmlDetector> checks) {
        assert res.isDirectory() : res;
        File[] resourceDirs = res.listFiles();
        if (resourceDirs == null) {
            return;
        }

        // Sort alphabetically such that we can process related folder types at the
        // same time

        Arrays.sort(resourceDirs);
        for (File dir : resourceDirs) {
            ResourceFolderType type = ResourceFolderType.getFolderType(dir.getName());
            if (type != null) {
                checkResourceFolder(project, main, dir, type, checks);
            }

            if (mCanceled) {
                return;
            }
        }
    }

    private void checkResourceFolder(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File dir,
            @NonNull ResourceFolderType type,
            @NonNull List<ResourceXmlDetector> checks) {
        // Process the resource folder
        File[] xmlFiles = dir.listFiles();
        if (xmlFiles != null && xmlFiles.length > 0) {
            XmlVisitor visitor = getVisitor(type, checks);
            if (visitor != null) { // if not, there are no applicable rules in this folder
                // Process files in alphabetical order, to ensure stable output
                // (for example for the duplicate resource detector)
                Arrays.sort(xmlFiles);
                for (File file : xmlFiles) {
                    if (LintUtils.isXmlFile(file)) {
                        XmlContext context = new XmlContext(this, project, main, file, type);
                        fireEvent(EventType.SCANNING_FILE, context);
                        visitor.visitFile(context, file);
                        if (mCanceled) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /** Checks individual resources */
    private void checkIndividualResources(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<ResourceXmlDetector> xmlDetectors,
            @NonNull List<File> files) {
        for (File file : files) {
            if (file.isDirectory()) {
                // Is it a resource folder?
                ResourceFolderType type = ResourceFolderType.getFolderType(file.getName());
                if (type != null && new File(file.getParentFile(), RES_FOLDER).exists()) {
                    // Yes.
                    checkResourceFolder(project, main, file, type, xmlDetectors);
                } else if (file.getName().equals(RES_FOLDER)) { // Is it the res folder?
                    // Yes
                    checkResFolder(project, main, file, xmlDetectors);
                } else {
                    mClient.log(null, "Unexpected folder %1$s; should be project, " +
                            "\"res\" folder or resource folder", file.getPath());
                    continue;
                }
            } else if (file.isFile() && LintUtils.isXmlFile(file)) {
                // Yes, find out its resource type
                String folderName = file.getParentFile().getName();
                ResourceFolderType type = ResourceFolderType.getFolderType(folderName);
                if (type != null) {
                    XmlVisitor visitor = getVisitor(type, xmlDetectors);
                    if (visitor != null) {
                        XmlContext context = new XmlContext(this, project, main, file, type);
                        fireEvent(EventType.SCANNING_FILE, context);
                        visitor.visitFile(context, file);
                    }
                }
            }
        }
    }

    /**
     * Adds a listener to be notified of lint progress
     *
     * @param listener the listener to be added
     */
    public void addLintListener(@NonNull LintListener listener) {
        if (mListeners == null) {
            mListeners = new ArrayList<LintListener>(1);
        }
        mListeners.add(listener);
    }

    /**
     * Removes a listener such that it is no longer notified of progress
     *
     * @param listener the listener to be removed
     */
    public void removeLintListener(@NonNull LintListener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            mListeners = null;
        }
    }

    /** Notifies listeners, if any, that the given event has occurred */
    private void fireEvent(@NonNull LintListener.EventType type, @Nullable Context context) {
        if (mListeners != null) {
            for (LintListener listener : mListeners) {
                listener.update(this, type, context);
            }
        }
    }

    /**
     * Wrapper around the lint client. This sits in the middle between a
     * detector calling for example {@link LintClient#report} and
     * the actual embedding tool, and performs filtering etc such that detectors
     * and lint clients don't have to make sure they check for ignored issues or
     * filtered out warnings.
     */
    private class LintClientWrapper extends LintClient {
        @NonNull
        private final LintClient mDelegate;

        public LintClientWrapper(@NonNull LintClient delegate) {
            mDelegate = delegate;
        }

        @Override
        public void report(
                @NonNull Context context,
                @NonNull Issue issue,
                @NonNull Severity severity,
                @Nullable Location location,
                @NonNull String message,
                @Nullable Object data) {
            assert mCurrentProject != null;
            if (!mCurrentProject.getReportIssues()) {
                return;
            }

            Configuration configuration = context.getConfiguration();
            if (!configuration.isEnabled(issue)) {
                if (issue != IssueRegistry.PARSER_ERROR && issue != IssueRegistry.LINT_ERROR) {
                    mDelegate.log(null, "Incorrect detector reported disabled issue %1$s",
                            issue.toString());
                }
                return;
            }

            if (configuration.isIgnored(context, issue, location, message, data)) {
                return;
            }

            if (severity == Severity.IGNORE) {
                return;
            }

            mDelegate.report(context, issue, severity, location, message, data);
        }

        // Everything else just delegates to the embedding lint client

        @Override
        @NonNull
        public Configuration getConfiguration(@NonNull Project project) {
            return mDelegate.getConfiguration(project);
        }


        @Override
        public void log(@NonNull Severity severity, @Nullable Throwable exception,
                @Nullable String format, @Nullable Object... args) {
            mDelegate.log(exception, format, args);
        }

        @Override
        @NonNull
        public String readFile(@NonNull File file) {
            return mDelegate.readFile(file);
        }

        @Override
        @NonNull
        public byte[] readBytes(@NonNull File file) throws IOException {
            return mDelegate.readBytes(file);
        }

        @Override
        @NonNull
        public List<File> getJavaSourceFolders(@NonNull Project project) {
            return mDelegate.getJavaSourceFolders(project);
        }

        @Override
        @NonNull
        public List<File> getJavaClassFolders(@NonNull Project project) {
            return mDelegate.getJavaClassFolders(project);
        }

        @NonNull
        @Override
        public List<File> getJavaLibraries(@NonNull Project project) {
            return mDelegate.getJavaLibraries(project);
        }

        @Override
        @NonNull
        public List<File> getResourceFolders(@NonNull Project project) {
            return mDelegate.getResourceFolders(project);
        }

        @Override
        @Nullable
        public IDomParser getDomParser() {
            return mDelegate.getDomParser();
        }

        @Override
        @NonNull
        public Class<? extends Detector> replaceDetector(
                @NonNull Class<? extends Detector> detectorClass) {
            return mDelegate.replaceDetector(detectorClass);
        }

        @Override
        @NonNull
        public SdkInfo getSdkInfo(@NonNull Project project) {
            return mDelegate.getSdkInfo(project);
        }

        @Override
        @NonNull
        public Project getProject(@NonNull File dir, @NonNull File referenceDir) {
            return mDelegate.getProject(dir, referenceDir);
        }

        @Override
        @Nullable
        public IJavaParser getJavaParser() {
            return mDelegate.getJavaParser();
        }

        @Override
        public File findResource(@NonNull String relativePath) {
            return mDelegate.findResource(relativePath);
        }

        @Override
        @Nullable
        public File getCacheDir(boolean create) {
            return mDelegate.getCacheDir(create);
        }

        @Override
        @NonNull
        protected ClassPathInfo getClassPath(@NonNull Project project) {
            return mDelegate.getClassPath(project);
        }

        @Override
        public void log(@Nullable Throwable exception, @Nullable String format,
                @Nullable Object... args) {
            mDelegate.log(exception, format, args);
        }

        @Override
        @Nullable
        public File getSdkHome() {
            return mDelegate.getSdkHome();
        }

        @Override
        @NonNull
        public IAndroidTarget[] getTargets() {
            return mDelegate.getTargets();
        }

        @Override
        public int getHighestKnownApiLevel() {
            return mDelegate.getHighestKnownApiLevel();
        }

        @Override
        @Nullable
        public String getSuperClass(@NonNull Project project, @NonNull String name) {
            return mDelegate.getSuperClass(project, name);
        }

        @Override
        @Nullable
        public Boolean isSubclassOf(@NonNull Project project, @NonNull String name,
                @NonNull String superClassName) {
            return mDelegate.isSubclassOf(project, name, superClassName);
        }

        @Override
        @NonNull
        public String getProjectName(@NonNull Project project) {
            return mDelegate.getProjectName(project);
        }

        @Override
        public boolean isGradleProject(Project project) {
            return mDelegate.isGradleProject(project);
        }

        @NonNull
        @Override
        protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
            return mDelegate.createProject(dir, referenceDir);
        }

        @NonNull
        @Override
        public List<File> findGlobalRuleJars() {
            return mDelegate.findGlobalRuleJars();
        }

        @NonNull
        @Override
        public List<File> findRuleJars(@NonNull Project project) {
            return mDelegate.findRuleJars(project);
        }

        @Override
        public boolean isProjectDirectory(@NonNull File dir) {
            return mDelegate.isProjectDirectory(dir);
        }

        @Override
        public void registerProject(@NonNull File dir, @NonNull Project project) {
            log(Severity.WARNING, null, "Too late to register projects");
            mDelegate.registerProject(dir, project);
        }
    }

    /**
     * Requests another pass through the data for the given detector. This is
     * typically done when a detector needs to do more expensive computation,
     * but it only wants to do this once it <b>knows</b> that an error is
     * present, or once it knows more specifically what to check for.
     *
     * @param detector the detector that should be included in the next pass.
     *            Note that the lint runner may refuse to run more than a couple
     *            of runs.
     * @param scope the scope to be revisited. This must be a subset of the
     *       current scope ({@link #getScope()}, and it is just a performance hint;
     *       in particular, the detector should be prepared to be called on other
     *       scopes as well (since they may have been requested by other detectors).
     *       You can pall null to indicate "all".
     */
    public void requestRepeat(@NonNull Detector detector, @Nullable EnumSet<Scope> scope) {
        if (mRepeatingDetectors == null) {
            mRepeatingDetectors = new ArrayList<Detector>();
        }
        mRepeatingDetectors.add(detector);

        if (scope != null) {
            if (mRepeatScope == null) {
                mRepeatScope = scope;
            } else {
                mRepeatScope = EnumSet.copyOf(mRepeatScope);
                mRepeatScope.addAll(scope);
            }
        } else {
            mRepeatScope = Scope.ALL;
        }
    }

    // Unfortunately, ASMs nodes do not extend a common DOM node type with parent
    // pointers, so we have to have multiple methods which pass in each type
    // of node (class, method, field) to be checked.

    /**
     * Returns whether the given issue is suppressed in the given method.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     * @param classNode the class containing the issue
     * @param method the method containing the issue
     * @param instruction the instruction within the method, if any
     * @return true if there is a suppress annotation covering the specific
     *         issue on this method
     */
    public boolean isSuppressed(
            @Nullable Issue issue,
            @NonNull ClassNode classNode,
            @NonNull MethodNode method,
            @Nullable AbstractInsnNode instruction) {
        if (method.invisibleAnnotations != null) {
            @SuppressWarnings("unchecked")
            List<AnnotationNode> annotations = method.invisibleAnnotations;
            return isSuppressed(issue, annotations);
        }

        // Initializations of fields end up placed in generated methods (<init>
        // for members and <clinit> for static fields).
        if (instruction != null && method.name.charAt(0) == '<') {
            AbstractInsnNode next = LintUtils.getNextInstruction(instruction);
            if (next != null && next.getType() == AbstractInsnNode.FIELD_INSN) {
                FieldInsnNode fieldRef = (FieldInsnNode) next;
                FieldNode field = findField(classNode, fieldRef.owner, fieldRef.name);
                if (field != null && isSuppressed(issue, field)) {
                    return true;
                }
            } else if (classNode.outerClass != null && classNode.outerMethod == null
                        && isAnonymousClass(classNode)) {
                if (isSuppressed(issue, classNode)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    private static MethodInsnNode findConstructorInvocation(
            @NonNull MethodNode method,
            @NonNull String className) {
        InsnList nodes = ((MethodNode) method).instructions;
        for (int i = 0, n = nodes.size(); i < n; i++) {
            AbstractInsnNode instruction = nodes.get(i);
            if (instruction.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (className.equals(call.owner)) {
                    return call;
                }
            }
        }

        return null;
    }

    @Nullable
    private FieldNode findField(
            @NonNull ClassNode classNode,
            @NonNull String owner,
            @NonNull String name) {
        ClassNode current = classNode;
        while (current != null) {
            if (owner.equals(current.name)) {
                @SuppressWarnings("rawtypes") // ASM API
                List fieldList = current.fields;
                for (Object f : fieldList) {
                    FieldNode field = (FieldNode) f;
                    if (field.name.equals(name)) {
                        return field;
                    }
                }
                return null;
            }
            current = getOuterClassNode(current);
        }
        return null;
    }

    @Nullable
    private MethodNode findMethod(
            @NonNull ClassNode classNode,
            @NonNull String name,
            boolean includeInherited) {
        ClassNode current = classNode;
        while (current != null) {
            @SuppressWarnings("rawtypes") // ASM API
            List methodList = current.methods;
            for (Object f : methodList) {
                MethodNode method = (MethodNode) f;
                if (method.name.equals(name)) {
                    return method;
                }
            }

            if (includeInherited) {
                current = getOuterClassNode(current);
            } else {
                break;
            }
        }
        return null;
    }

    /**
     * Returns whether the given issue is suppressed for the given field.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     * @param field the field potentially annotated with a suppress annotation
     * @return true if there is a suppress annotation covering the specific
     *         issue on this field
     */
    public boolean isSuppressed(@Nullable Issue issue, @NonNull FieldNode field) {
        if (field.invisibleAnnotations != null) {
            @SuppressWarnings("unchecked")
            List<AnnotationNode> annotations = field.invisibleAnnotations;
            return isSuppressed(issue, annotations);
        }

        return false;
    }

    /**
     * Returns whether the given issue is suppressed in the given class.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     * @param classNode the class containing the issue
     * @return true if there is a suppress annotation covering the specific
     *         issue in this class
     */
    public boolean isSuppressed(@Nullable Issue issue, @NonNull ClassNode classNode) {
        if (classNode.invisibleAnnotations != null) {
            @SuppressWarnings("unchecked")
            List<AnnotationNode> annotations = classNode.invisibleAnnotations;
            return isSuppressed(issue, annotations);
        }

        if (classNode.outerClass != null && classNode.outerMethod == null
                && isAnonymousClass(classNode)) {
            ClassNode outer = getOuterClassNode(classNode);
            if (outer != null) {
                MethodNode m = findMethod(outer, CONSTRUCTOR_NAME, false);
                if (m != null) {
                    MethodInsnNode call = findConstructorInvocation(m, classNode.name);
                    if (call != null) {
                        if (isSuppressed(issue, outer, m, call)) {
                            return true;
                        }
                    }
                }
                m = findMethod(outer, CLASS_CONSTRUCTOR, false);
                if (m != null) {
                    MethodInsnNode call = findConstructorInvocation(m, classNode.name);
                    if (call != null) {
                        if (isSuppressed(issue, outer, m, call)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isSuppressed(@Nullable Issue issue, List<AnnotationNode> annotations) {
        for (AnnotationNode annotation : annotations) {
            String desc = annotation.desc;

            // We could obey @SuppressWarnings("all") too, but no need to look for it
            // because that annotation only has source retention.

            if (desc.endsWith(SUPPRESS_LINT_VMSIG)) {
                if (annotation.values != null) {
                    for (int i = 0, n = annotation.values.size(); i < n; i += 2) {
                        String key = (String) annotation.values.get(i);
                        if (key.equals("value")) {   //$NON-NLS-1$
                            Object value = annotation.values.get(i + 1);
                            if (value instanceof String) {
                                String id = (String) value;
                                if (id.equalsIgnoreCase(SUPPRESS_ALL) ||
                                        issue != null && id.equalsIgnoreCase(issue.getId())) {
                                    return true;
                                }
                            } else if (value instanceof List) {
                                @SuppressWarnings("rawtypes")
                                List list = (List) value;
                                for (Object v : list) {
                                    if (v instanceof String) {
                                        String id = (String) v;
                                        if (id.equalsIgnoreCase(SUPPRESS_ALL) || (issue != null
                                                && id.equalsIgnoreCase(issue.getId()))) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns whether the given issue is suppressed in the given parse tree node.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     * @param scope the AST node containing the issue
     * @return true if there is a suppress annotation covering the specific
     *         issue in this class
     */
    public boolean isSuppressed(@NonNull Issue issue, @Nullable Node scope) {
        while (scope != null) {
            Class<? extends Node> type = scope.getClass();
            // The Lombok AST uses a flat hierarchy of node type implementation classes
            // so no need to do instanceof stuff here.
            if (type == VariableDefinition.class) {
                // Variable
                VariableDefinition declaration = (VariableDefinition) scope;
                if (isSuppressed(issue, declaration.astModifiers())) {
                    return true;
                }
            } else if (type == MethodDeclaration.class) {
                // Method
                // Look for annotations on the method
                MethodDeclaration declaration = (MethodDeclaration) scope;
                if (isSuppressed(issue, declaration.astModifiers())) {
                    return true;
                }
            } else if (type == ConstructorDeclaration.class) {
                // Constructor
                // Look for annotations on the method
                ConstructorDeclaration declaration = (ConstructorDeclaration) scope;
                if (isSuppressed(issue, declaration.astModifiers())) {
                    return true;
                }
            } else if (type == ClassDeclaration.class) {
                // Class
                ClassDeclaration declaration = (ClassDeclaration) scope;
                if (isSuppressed(issue, declaration.astModifiers())) {
                    return true;
                }
            }

            scope = scope.getParent();
        }

        return false;
    }

    /**
     * Returns true if the given AST modifier has a suppress annotation for the
     * given issue (which can be null to check for the "all" annotation)
     *
     * @param issue the issue to be checked
     * @param modifiers the modifier to check
     * @return true if the issue or all issues should be suppressed for this
     *         modifier
     */
    private static boolean isSuppressed(@Nullable Issue issue, @Nullable Modifiers modifiers) {
        if (modifiers == null) {
            return false;
        }
        StrictListAccessor<Annotation, Modifiers> annotations = modifiers.astAnnotations();
        if (annotations == null) {
            return false;
        }

        Iterator<Annotation> iterator = annotations.iterator();
        while (iterator.hasNext()) {
            Annotation annotation = iterator.next();
            TypeReference t = annotation.astAnnotationTypeReference();
            String typeName = t.getTypeName();
            if (typeName.endsWith(SUPPRESS_LINT)
                    || typeName.endsWith("SuppressWarnings")) {     //$NON-NLS-1$
                StrictListAccessor<AnnotationElement, Annotation> values =
                        annotation.astElements();
                if (values != null) {
                    Iterator<AnnotationElement> valueIterator = values.iterator();
                    while (valueIterator.hasNext()) {
                        AnnotationElement element = valueIterator.next();
                        AnnotationValue valueNode = element.astValue();
                        if (valueNode == null) {
                            continue;
                        }
                        if (valueNode instanceof StringLiteral) {
                            StringLiteral literal = (StringLiteral) valueNode;
                            String value = literal.astValue();
                            if (value.equalsIgnoreCase(SUPPRESS_ALL) ||
                                    issue != null && issue.getId().equalsIgnoreCase(value)) {
                                return true;
                            }
                        } else if (valueNode instanceof ArrayInitializer) {
                            ArrayInitializer array = (ArrayInitializer) valueNode;
                            StrictListAccessor<Expression, ArrayInitializer> expressions =
                                    array.astExpressions();
                            if (expressions == null) {
                                continue;
                            }
                            Iterator<Expression> arrayIterator = expressions.iterator();
                            while (arrayIterator.hasNext()) {
                                Expression arrayElement = arrayIterator.next();
                                if (arrayElement instanceof StringLiteral) {
                                    String value = ((StringLiteral) arrayElement).astValue();
                                    if (value.equalsIgnoreCase(SUPPRESS_ALL) || (issue != null
                                            && issue.getId().equalsIgnoreCase(value))) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns whether the given issue is suppressed in the given XML DOM node.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     * @param node the DOM node containing the issue
     * @return true if there is a suppress annotation covering the specific
     *         issue in this class
     */
    public boolean isSuppressed(@NonNull Issue issue, @Nullable org.w3c.dom.Node node) {
        if (node instanceof Attr) {
            node = ((Attr) node).getOwnerElement();
        }
        while (node != null) {
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (element.hasAttributeNS(TOOLS_URI, ATTR_IGNORE)) {
                    String ignore = element.getAttributeNS(TOOLS_URI, ATTR_IGNORE);
                    if (ignore.indexOf(',') == -1) {
                        if (ignore.equalsIgnoreCase(SUPPRESS_ALL) || (issue != null
                                && issue.getId().equalsIgnoreCase(ignore))) {
                            return true;
                        }
                    } else {
                        for (String id : ignore.split(",")) { //$NON-NLS-1$
                            if (id.equalsIgnoreCase(SUPPRESS_ALL) || (issue != null
                                    && issue.getId().equalsIgnoreCase(id))) {
                                return true;
                            }
                        }
                    }
                }
            }

            node = node.getParentNode();
        }

        return false;
    }

    /** A pending class to be analyzed by {@link #checkClasses} */
    @VisibleForTesting
    static class ClassEntry implements Comparable<ClassEntry> {
        public final File file;
        public final File jarFile;
        public final File binDir;
        public final byte[] bytes;

        public ClassEntry(File file, File jarFile, File binDir, byte[] bytes) {
            super();
            this.file = file;
            this.jarFile = jarFile;
            this.binDir = binDir;
            this.bytes = bytes;
        }

        public String path() {
            if (jarFile != null) {
                return jarFile.getPath() + ':' + file.getPath();
            } else {
                return file.getPath();
            }
        }

        @Override
        public int compareTo(ClassEntry other) {
            String p1 = file.getPath();
            String p2 = other.file.getPath();
            int m1 = p1.length();
            int m2 = p2.length();
            int m = Math.min(m1, m2);

            for (int i = 0; i < m; i++) {
                char c1 = p1.charAt(i);
                char c2 = p2.charAt(i);
                if (c1 != c2) {
                    // Sort Foo$Bar.class *after* Foo.class, even though $ < .
                    if (c1 == '.' && c2 == '$') {
                        return -1;
                    }
                    if (c1 == '$' && c2 == '.') {
                        return 1;
                    }
                    return c1 - c2;
                }
            }

            return (m == m1) ? -1 : 1;
        }

        @Override
        public String toString() {
            return file.getPath();
        }
    }
}
