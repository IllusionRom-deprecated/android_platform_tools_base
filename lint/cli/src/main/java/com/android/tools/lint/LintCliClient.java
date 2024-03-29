/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint;

import static com.android.tools.lint.LintCliFlags.ERRNO_ERRORS;
import static com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS;
import static com.android.tools.lint.client.api.IssueRegistry.LINT_ERROR;
import static com.android.tools.lint.client.api.IssueRegistry.PARSER_ERROR;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.annotations.Beta;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Lint client for command line usage. Supports the flags in {@link LintCliFlags},
 * and offers text, HTML and XML reporting, etc.
 * <p>
 * Minimal example:
 * <pre>
 * // files is a list of java.io.Files, typically a directory containing
 * // lint projects or direct references to project root directories
 * IssueRegistry registry = new BuiltinIssueRegistry();
 * LintCliFlags flags = new LintCliFlags();
 * LintCliClient client = new LintCliClient(flags);
 * int exitCode = client.run(registry, files);
 * </pre>
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class LintCliClient extends LintClient {
    protected final List<Warning> mWarnings = new ArrayList<Warning>();
    protected boolean mHasErrors;
    protected int mErrorCount;
    protected int mWarningCount;
    protected IssueRegistry mRegistry;
    protected LintDriver mDriver;
    protected final LintCliFlags mFlags;
    private Configuration mConfiguration;

    /** Creates a CLI driver */
    public LintCliClient() {
        mFlags = new LintCliFlags();
        TextReporter reporter = new TextReporter(this, mFlags, new PrintWriter(System.out, true),
                false);
        mFlags.getReporters().add(reporter);
    }

    public LintCliClient(LintCliFlags flags) {
        mFlags = flags;
    }

    /**
     * Runs the static analysis command line driver. You need to add at least one error reporter
     * to the command line flags.
     */
    public int run(@NonNull IssueRegistry registry, @NonNull List<File> files) throws IOException {
        assert !mFlags.getReporters().isEmpty();
        mRegistry = registry;
        mDriver = new LintDriver(registry, this);


        mDriver.setAbbreviating(!mFlags.isShowEverything());
        if (!mFlags.isQuiet()) {
            mDriver.addLintListener(new ProgressPrinter());
        }

        mDriver.analyze(createLintRequest(files));

        Collections.sort(mWarnings);

        for (Reporter reporter : mFlags.getReporters()) {
            reporter.write(mErrorCount, mWarningCount, mWarnings);
        }

        return mFlags.isSetExitCode() ? (mHasErrors ? ERRNO_ERRORS : ERRNO_SUCCESS) : ERRNO_SUCCESS;
    }

    /** Creates a lint request */
    @NonNull
    protected LintRequest createLintRequest(@NonNull List<File> files) {
        return new LintRequest(this, files);
    }

    @Override
    public void log(
            @NonNull Severity severity,
            @Nullable Throwable exception,
            @Nullable String format,
            @Nullable Object... args) {
        System.out.flush();
        if (!mFlags.isQuiet()) {
            // Place the error message on a line of its own since we're printing '.' etc
            // with newlines during analysis
            System.err.println();
        }
        if (format != null) {
            System.err.println(String.format(format, args));
        }
        if (exception != null) {
            exception.printStackTrace();
        }
    }

    @Override
    public IDomParser getDomParser() {
        return new LintCliXmlParser();
    }

    @Override
    public Configuration getConfiguration(@NonNull Project project) {
        return new CliConfiguration(getConfiguration(), project);
    }

    /** File content cache */
    private final Map<File, String> mFileContents = new HashMap<File, String>(100);

    /** Read the contents of the given file, possibly cached */
    private String getContents(File file) {
        String s = mFileContents.get(file);
        if (s == null) {
            s = readFile(file);
            mFileContents.put(file, s);
        }

        return s;
    }

    @Override
    public IJavaParser getJavaParser() {
        return new LombokParser();
    }

    @Override
    public void report(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @Nullable Location location,
            @NonNull String message,
            @Nullable Object data) {
        assert context.isEnabled(issue) || issue == LINT_ERROR;

        if (severity == Severity.IGNORE) {
            return;
        }

        if (severity == Severity.ERROR || severity == Severity.FATAL) {
            mHasErrors = true;
            mErrorCount++;
        } else {
            mWarningCount++;
        }

        Warning warning = new Warning(issue, message, severity, context.getProject(), data);
        mWarnings.add(warning);

        if (location != null) {
            warning.location = location;
            File file = location.getFile();
            if (file != null) {
                warning.file = file;
                warning.path = getDisplayPath(context.getProject(), file);
            }

            Position startPosition = location.getStart();
            if (startPosition != null) {
                int line = startPosition.getLine();
                warning.line = line;
                warning.offset = startPosition.getOffset();
                if (line >= 0) {
                    if (context.file == location.getFile()) {
                        warning.fileContents = context.getContents();
                    }
                    if (warning.fileContents == null) {
                        warning.fileContents = getContents(location.getFile());
                    }

                    if (mFlags.isShowSourceLines()) {
                        // Compute error line contents
                        warning.errorLine = getLine(warning.fileContents, line);
                        if (warning.errorLine != null) {
                            // Replace tabs with spaces such that the column
                            // marker (^) lines up properly:
                            warning.errorLine = warning.errorLine.replace('\t', ' ');
                            int column = startPosition.getColumn();
                            if (column < 0) {
                                column = 0;
                                for (int i = 0; i < warning.errorLine.length(); i++, column++) {
                                    if (!Character.isWhitespace(warning.errorLine.charAt(i))) {
                                        break;
                                    }
                                }
                            }
                            StringBuilder sb = new StringBuilder(100);
                            sb.append(warning.errorLine);
                            sb.append('\n');
                            for (int i = 0; i < column; i++) {
                                sb.append(' ');
                            }

                            boolean displayCaret = true;
                            Position endPosition = location.getEnd();
                            if (endPosition != null) {
                                int endLine = endPosition.getLine();
                                int endColumn = endPosition.getColumn();
                                if (endLine == line && endColumn > column) {
                                    for (int i = column; i < endColumn; i++) {
                                        sb.append('~');
                                    }
                                    displayCaret = false;
                                }
                            }

                            if (displayCaret) {
                                sb.append('^');
                            }
                            sb.append('\n');
                            warning.errorLine = sb.toString();
                        }
                    }
                }
            }
        }
    }

    /** Look up the contents of the given line */
    static String getLine(String contents, int line) {
        int index = getLineOffset(contents, line);
        if (index != -1) {
            return getLineOfOffset(contents, index);
        } else {
            return null;
        }
    }

    static String getLineOfOffset(String contents, int offset) {
        int end = contents.indexOf('\n', offset);
        if (end == -1) {
            end = contents.indexOf('\r', offset);
        }
        return contents.substring(offset, end != -1 ? end : contents.length());
    }


    /** Look up the contents of the given line */
    static int getLineOffset(String contents, int line) {
        int index = 0;
        for (int i = 0; i < line; i++) {
            index = contents.indexOf('\n', index);
            if (index == -1) {
                return -1;
            }
            index++;
        }

        return index;
    }

    @NonNull
    @Override
    public String readFile(@NonNull File file) {
        try {
            return LintUtils.getEncodedString(this, file);
        } catch (IOException e) {
            return ""; //$NON-NLS-1$
        }
    }

    boolean isCheckingSpecificIssues() {
        return mFlags.getExactCheckedIds() != null;
    }

    private Map<Project, ClassPathInfo> mProjectInfo;

    @Override
    @NonNull
    protected ClassPathInfo getClassPath(@NonNull Project project) {
        ClassPathInfo classPath = super.getClassPath(project);

        List<File> sources = mFlags.getSourcesOverride();
        List<File> classes = mFlags.getClassesOverride();
        List<File> libraries = mFlags.getLibrariesOverride();
        if (classes == null && sources == null && libraries == null) {
            return classPath;
        }

        ClassPathInfo info;
        if (mProjectInfo == null) {
            mProjectInfo = Maps.newHashMap();
            info = null;
        } else {
            info = mProjectInfo.get(project);
        }

        if (info == null) {
            if (sources == null) {
                sources = classPath.getSourceFolders();
            }
            if (classes == null) {
                classes = classPath.getClassFolders();
            }
            if (libraries == null) {
                libraries = classPath.getLibraries();
            }

            info = new ClassPathInfo(sources, classes, libraries);
            mProjectInfo.put(project, info);
        }

        return info;
    }

    @NonNull
    @Override
    public List<File> getResourceFolders(@NonNull Project project) {
        List<File> resources = mFlags.getResourcesOverride();
        if (resources == null) {
            return super.getResourceFolders(project);
        }

        return resources;
    }

    /**
     * Consult the lint.xml file, but override with the --enable and --disable
     * flags supplied on the command line
     */
    class CliConfiguration extends DefaultConfiguration {
        CliConfiguration(@NonNull Configuration parent, @NonNull Project project) {
            super(LintCliClient.this, project, parent);
        }

        CliConfiguration(File lintFile) {
            super(LintCliClient.this, null /*project*/, null /*parent*/, lintFile);
        }

        @NonNull
        @Override
        public Severity getSeverity(@NonNull Issue issue) {
            Severity severity = computeSeverity(issue);

            if (mFlags.isWarningsAsErrors() && severity != Severity.IGNORE) {
                severity = Severity.ERROR;
            }

            if (mFlags.isIgnoreWarnings() && severity == Severity.WARNING) {
                severity = Severity.IGNORE;
            }

            return severity;
        }

        @NonNull
        @Override
        protected Severity getDefaultSeverity(@NonNull Issue issue) {
            if (mFlags.isCheckAllWarnings()) {
                return issue.getDefaultSeverity();
            }

            return super.getDefaultSeverity(issue);
        }

        private Severity computeSeverity(@NonNull Issue issue) {
            Severity severity = super.getSeverity(issue);

            String id = issue.getId();
            Set<String> suppress = mFlags.getSuppressedIds();
            if (suppress.contains(id)) {
                return Severity.IGNORE;
            }

            Set<String> enabled = mFlags.getEnabledIds();
            Set<String> check = mFlags.getExactCheckedIds();
            if (enabled.contains(id) || (check != null && check.contains(id))) {
                // Overriding default
                // Detectors shouldn't be returning ignore as a default severity,
                // but in case they do, force it up to warning here to ensure that
                // it's run
                if (severity == Severity.IGNORE) {
                    severity = issue.getDefaultSeverity();
                    if (severity == Severity.IGNORE) {
                        severity = Severity.WARNING;
                    }
                }

                return severity;
            }

            if (check != null && issue != LINT_ERROR && issue != PARSER_ERROR) {
                return Severity.IGNORE;
            }

            return severity;
        }
    }

    private static class ProgressPrinter implements LintListener {
        @Override
        public void update(
                @NonNull LintDriver lint,
                @NonNull EventType type,
                @Nullable Context context) {
            switch (type) {
                case SCANNING_PROJECT: {
                    String name = context != null ? context.getProject().getName() : "?";
                    if (lint.getPhase() > 1) {
                        System.out.print(String.format(
                                "\nScanning %1$s (Phase %2$d): ",
                                name,
                                lint.getPhase()));
                    } else {
                        System.out.print(String.format(
                                "\nScanning %1$s: ",
                                name));
                    }
                    break;
                }
                case SCANNING_LIBRARY_PROJECT: {
                    String name = context != null ? context.getProject().getName() : "?";
                    System.out.print(String.format(
                            "\n         - %1$s: ",
                            name));
                    break;
                }
                case SCANNING_FILE:
                    System.out.print('.');
                    break;
                case NEW_PHASE:
                    // Ignored for now: printing status as part of next project's status
                    break;
                case CANCELED:
                case COMPLETED:
                    System.out.println();
                    break;
                case STARTING:
                    // Ignored for now
                    break;
            }
        }
    }

    /**
     * Given a file, it produces a cleaned up path from the file.
     * This will clean up the path such that
     * <ul>
     *   <li>  {@code foo/./bar} becomes {@code foo/bar}
     *   <li>  {@code foo/bar/../baz} becomes {@code foo/baz}
     * </ul>
     *
     * Unlike {@link java.io.File#getCanonicalPath()} however, it will <b>not</b> attempt
     * to make the file canonical, such as expanding symlinks and network mounts.
     *
     * @param file the file to compute a clean path for
     * @return the cleaned up path
     */
    @VisibleForTesting
    @NonNull
    static String getCleanPath(@NonNull File file) {
        String path = file.getPath();
        StringBuilder sb = new StringBuilder(path.length());

        if (path.startsWith(File.separator)) {
            sb.append(File.separator);
        }
        elementLoop:
        for (String element : Splitter.on(File.separatorChar).omitEmptyStrings().split(path)) {
            if (element.equals(".")) {          //$NON-NLS-1$
                continue;
            } else if (element.equals("..")) {  //$NON-NLS-1$
                if (sb.length() > 0) {
                    for (int i = sb.length() - 1; i >= 0; i--) {
                        char c = sb.charAt(i);
                        if (c == File.separatorChar) {
                            sb.setLength(i == 0 ? 1 : i);
                            continue elementLoop;
                        }
                    }
                    sb.setLength(0);
                    continue;
                }
            }

            if (sb.length() > 1) {
                sb.append(File.separatorChar);
            } else if (sb.length() > 0 && sb.charAt(0) != File.separatorChar) {
                sb.append(File.separatorChar);
            }
            sb.append(element);
        }
        if (path.endsWith(File.separator) && sb.length() > 0
                && sb.charAt(sb.length() - 1) != File.separatorChar) {
            sb.append(File.separator);
        }

        return sb.toString();
    }

    String getDisplayPath(Project project, File file) {
        String path = file.getPath();
        if (!mFlags.isFullPath() && path.startsWith(project.getReferenceDir().getPath())) {
            int chop = project.getReferenceDir().getPath().length();
            if (path.length() > chop && path.charAt(chop) == File.separatorChar) {
                chop++;
            }
            path = path.substring(chop);
            if (path.isEmpty()) {
                path = file.getName();
            }
        } else if (mFlags.isFullPath()) {
            path = getCleanPath(file.getAbsoluteFile());
        }

        return path;
    }

    /** Returns whether all warnings are enabled, including those disabled by default */
    boolean isAllEnabled() {
        return mFlags.isCheckAllWarnings();
    }

    /** Returns the issue registry used by this client */
    IssueRegistry getRegistry() {
        return mRegistry;
    }

    /** Returns the driver running the lint checks */
    LintDriver getDriver() {
        return mDriver;
    }

    /** Returns the configuration used by this client */
    Configuration getConfiguration() {
        if (mConfiguration == null) {
            File configFile = mFlags.getDefaultConfiguration();
            if (configFile != null) {
                if (!configFile.exists()) {
                    log(Severity.ERROR, null, "Warning: Configuration file %1$s does not exist",
                            configFile);
                }
                mConfiguration = createConfigurationFromFile(configFile);
            }
        }

        return mConfiguration;
    }

    /** Returns true if the given issue has been explicitly disabled */
    boolean isSuppressed(Issue issue) {
        return mFlags.getSuppressedIds().contains(issue.getId());
    }

    public Configuration createConfigurationFromFile(File file) {
        return new CliConfiguration(file);
    }

    @SuppressWarnings("resource") // Eclipse doesn't know about Closeables.closeQuietly
    @Nullable
    String getRevision() {
        File file = findResource("tools" + File.separator +     //$NON-NLS-1$
                "source.properties");          //$NON-NLS-1$
        if (file != null && file.exists()) {
            FileInputStream input = null;
            try {
                input = new FileInputStream(file);
                Properties properties = new Properties();
                properties.load(input);

                String revision = properties.getProperty("Pkg.Revision"); //$NON-NLS-1$
                if (revision != null && !revision.isEmpty()) {
                    return revision;
                }
            } catch (IOException e) {
                // Couldn't find or read the version info: just print out unknown below
            } finally {
                Closeables.closeQuietly(input);
            }
        }

        return null;
    }

    public boolean haveErrors() {
        return mErrorCount > 0;
    }
}
