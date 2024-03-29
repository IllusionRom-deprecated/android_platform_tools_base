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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_MIN_SDK_VERSION;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_PERMISSION;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_LIBRARY;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.TAG_USES_SDK;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_DATA;
import static com.android.xml.AndroidManifest.NODE_METADATA;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Maps;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for issues in AndroidManifest files such as declaring elements in the
 * wrong order.
 */
public class ManifestDetector extends Detector implements Detector.XmlScanner {
    private static final Implementation IMPLEMENTATION = new Implementation(
            ManifestDetector.class,
            Scope.MANIFEST_SCOPE
    );

    /** Wrong order of elements in the manifest */
    public static final Issue ORDER = Issue.create(
            "ManifestOrder", //$NON-NLS-1$
            "Incorrect order of elements in manifest",
            "Checks for manifest problems like `<uses-sdk>` after the `<application>` tag",
            "The <application> tag should appear after the elements which declare " +
            "which version you need, which features you need, which libraries you " +
            "need, and so on. In the past there have been subtle bugs (such as " +
            "themes not getting applied correctly) when the `<application>` tag appears " +
            "before some of these other elements, so it's best to order your " +
            "manifest in the logical dependency order.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Missing a {@code <uses-sdk>} element */
    public static final Issue USES_SDK = Issue.create(
            "UsesMinSdkAttributes", //$NON-NLS-1$
            "Minimum SDK and target SDK attributes not defined",
            "Checks that the minimum SDK and target SDK attributes are defined",

            "The manifest should contain a `<uses-sdk>` element which defines the " +
            "minimum API Level required for the application to run, " +
            "as well as the target version (the highest API level you have tested " +
            "the version for.)",

            Category.CORRECTNESS,
            9,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/guide/topics/manifest/uses-sdk-element.html"); //$NON-NLS-1$

    /** Using a targetSdkVersion that isn't recent */
    public static final Issue TARGET_NEWER = Issue.create(
            "OldTargetApi", //$NON-NLS-1$
            "Target SDK attribute is not targeting latest version",
            "Checks that the manifest specifies a targetSdkVersion that is recent",

            "When your application runs on a version of Android that is more recent than your " +
            "`targetSdkVersion` specifies that it has been tested with, various compatibility " +
            "modes kick in. This ensures that your application continues to work, but it may " +
            "look out of place. For example, if the `targetSdkVersion` is less than 14, your " +
            "app may get an option button in the UI.\n" +
            "\n" +
            "To fix this issue, set the `targetSdkVersion` to the highest available value. Then " +
            "test your app to make sure everything works correctly. You may want to consult " +
            "the compatibility notes to see what changes apply to each version you are adding " +
            "support for: " +
            "http://developer.android.com/reference/android/os/Build.VERSION_CODES.html",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/reference/android/os/Build.VERSION_CODES.html"); //$NON-NLS-1$

    /** Using multiple {@code <uses-sdk>} elements */
    public static final Issue MULTIPLE_USES_SDK = Issue.create(
            "MultipleUsesSdk", //$NON-NLS-1$
            "Multiple `<uses-sdk>` elements in the manifest",
            "Checks that the `<uses-sdk>` element appears at most once",

            "The `<uses-sdk>` element should appear just once; the tools will *not* merge the " +
            "contents of all the elements so if you split up the attributes across multiple " +
            "elements, only one of them will take effect. To fix this, just merge all the " +
            "attributes from the various elements into a single <uses-sdk> element.",

            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/guide/topics/manifest/uses-sdk-element.html"); //$NON-NLS-1$

    /** Missing a {@code <uses-sdk>} element */
    public static final Issue WRONG_PARENT = Issue.create(
            "WrongManifestParent", //$NON-NLS-1$
            "Wrong manifest parent",
            "Checks that various manifest elements are declared in the right place",

            "The `<uses-library>` element should be defined as a direct child of the " +
            "`<application>` tag, not the `<manifest>` tag or an `<activity>` tag. Similarly, " +
            "a `<uses-sdk>` tag much be declared at the root level, and so on. This check " +
            "looks for incorrect declaration locations in the manifest, and complains " +
            "if an element is found in the wrong place.",

            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/guide/topics/manifest/manifest-intro.html"); //$NON-NLS-1$

    /** Missing a {@code <uses-sdk>} element */
    public static final Issue DUPLICATE_ACTIVITY = Issue.create(
            "DuplicateActivity", //$NON-NLS-1$
            "Activity registered more than once",
            "Checks that an activity is registered only once in the manifest",

            "An activity should only be registered once in the manifest. If it is " +
            "accidentally registered more than once, then subtle errors can occur, " +
            "since attribute declarations from the two elements are not merged, so " +
            "you may accidentally remove previous declarations.",

            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Not explicitly defining allowBackup */
    public static final Issue ALLOW_BACKUP = Issue.create(
            "AllowBackup", //$NON-NLS-1$
            "Missing `allowBackup` attribute",
            "Ensure that allowBackup is explicitly set in the application's manifest",

            "The allowBackup attribute determines if an application's data can be backed up " +
            "and restored. It is documented at " +
            "http://developer.android.com/reference/android/R.attr.html#allowBackup\n" +
            "\n" +
            "By default, this flag is set to `true`. When this flag is set to `true`, " +
            "application data can be backed up and restored by the user using `adb backup` " +
            "and `adb restore`.\n" +
            "\n" +
            "This may have security consequences for an application. `adb backup` allows " +
            "users who have enabled USB debugging to copy application data off of the " +
            "device. Once backed up, all application data can be read by the user. " +
            "`adb restore` allows creation of application data from a source specified " +
            "by the user. Following a restore, applications should not assume that the " +
            "data, file permissions, and directory permissions were created by the " +
            "application itself.\n" +
            "\n" +
            "Setting `allowBackup=\"false\"` opts an application out of both backup and " +
            "restore.\n" +
            "\n" +
            "To fix this warning, decide whether your application should support backup, " +
            "and explicitly set `android:allowBackup=(true|false)\"`",

            Category.SECURITY,
            3,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/reference/android/R.attr.html#allowBackup");

    /** Conflicting permission names */
    public static final Issue UNIQUE_PERMISSION = Issue.create(
            "UniquePermission", //$NON-NLS-1$
            "Permission names are not unique",
            "Checks that permission names are unique",

            "The unqualified names or your permissions must be unique. The reason for this " +
                    "is that at build time, the `aapt` tool will generate a class named `Manifest` "
                    +
                    "which contains a field for each of your permissions. These fields are named " +
                    "using your permission unqualified names (i.e. the name portion after the last "
                    +
                    "dot).\n" +
                    "\n" +
                    "If more than one permission maps to the same field name, that field will " +
                    "arbitrarily name just one of them.",

            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Using a resource for attributes that do not allow it */
    public static final Issue SET_VERSION = Issue.create(
            "MissingVersion", //$NON-NLS-1$
            "Missing application name/version",
            "Checks that the application name and version are set",

            "You should define the version information for your application.\n" +
            "`android:versionCode`: An integer value that represents the version of the " +
            "application code, relative to other versions.\n" +
            "\n" +
            "`android:versionName`: A string value that represents the release version of " +
            "the application code, as it should be shown to users.",

            Category.CORRECTNESS,
            2,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/tools/publishing/versioning.html#appversioning");

    /** Using a resource for attributes that do not allow it */
    public static final Issue ILLEGAL_REFERENCE = Issue.create(
            "IllegalResourceRef", //$NON-NLS-1$
            "Name and version must be integer or string, not resource",
            "Checks for resource references where only literals are allowed",

            "For the `versionCode` attribute, you have to specify an actual integer " +
                    "literal; you cannot use an indirection with a `@dimen/name` resource. " +
                    "Similarly, the `versionName` attribute should be an actual string, not " +
                    "a string resource url.",

            Category.CORRECTNESS,
            8,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Declaring a uses-feature multiple time */
    public static final Issue DUPLICATE_USES_FEATURE = Issue.create(
            "DuplicateUsesFeature", //$NON-NLS-1$
            "Feature declared more than once",
            "Ensures you declare each hardware or software feature only once in the manifest",

            "A given feature should only be declared once in the manifest.",

            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Not explicitly defining application icon */
    public static final Issue APPLICATION_ICON = Issue.create(
            "MissingApplicationIcon", //$NON-NLS-1$
            "Missing application icon",
            "Checks that the application icon is set",

            "You should set an icon for the application as whole because there is no " +
            "default. This attribute must be set as a reference to a drawable resource " +
            "containing the image (for example `@drawable/icon`).",

            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/tools/publishing/preparing.html#publishing-configure"); //$NON-NLS-1$

    /** Malformed Device Admin */
    public static final Issue DEVICE_ADMIN = Issue.create(
            "DeviceAdmin", //$NON-NLS-1$
            "Malformed Device Admin",
            "Ensures that device admins are properly registered",
            "If you register a broadcast receiver which acts as a device admin, you must also " +
            "register an `<intent-filter>` for the action " +
            "`android.app.action.DEVICE_ADMIN_ENABLED`, without any `<data>`, such that the " +
            "device admin can be activated/deactivated.\n" +
            "\n" +
            "To do this, add\n" +
            "`<intent-filter>`\n" +
            "    `<action android:name=\"android.app.action.DEVICE_ADMIN_ENABLED\" />`\n" +
            "`</intent-filter>`\n" +
            "to your `<receiver>`.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Using a mock location in a non-debug-specific manifest file */
    public static final Issue MOCK_LOCATION = Issue.create(
            "MockLocation", //$NON-NLS-1$
            "Using mock location provider in production",
            "Checks that mock location providers are only used in debug builds",

            "Using a mock location provider (by requiring the permission " +
            "`android.permission.ACCESS_MOCK_LOCATION`) should *only* be done " +
            "in debug builds. In Gradle projects, that means you should only " +
            "request this permission in a debug source set specific manifest file.\n" +
            "\n" +
            "To fix this, create a new manifest file in the debug folder and move " +
            "the `<uses-permission>` element there. A typical path to a debug manifest " +
            "override file in a Gradle project is src/debug/AndroidManifest.xml.",

            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Permission name of mock location permission */
    public static final String MOCK_LOCATION_PERMISSION =
            "android.permission.ACCESS_MOCK_LOCATION";   //$NON-NLS-1$

    /** Constructs a new {@link ManifestDetector} check */
    public ManifestDetector() {
    }

    private boolean mSeenApplication;

    /** Number of times we've seen the <uses-sdk> element */
    private int mSeenUsesSdk;

    /** Activities we've encountered */
    private final Set<String> mActivities = new HashSet<String>();

    /** Features we've encountered */
    private final Set<String> mUsesFeatures = new HashSet<String>();

    /** Permission basenames */
    private Map<String, String> mPermissionNames;

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return file.getName().equals(ANDROID_MANIFEST_XML);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mSeenApplication = false;
        mSeenUsesSdk = 0;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        XmlContext xmlContext = (XmlContext) context;
        Element element = xmlContext.document.getDocumentElement();
        if (element != null) {
            checkDocumentElement(xmlContext, element);
        }

        if (mSeenUsesSdk == 0 && context.isEnabled(USES_SDK)
                // Not required in Gradle projects; typically defined in build.gradle instead
                // and inserted at build time
                && !context.getMainProject().isGradleProject()) {
            context.report(USES_SDK, Location.create(context.file),
                    "Manifest should specify a minimum API level with " +
                    "<uses-sdk android:minSdkVersion=\"?\" />; if it really supports " +
                    "all versions of Android set it to 1.", null);
        }
    }

    private static void checkDocumentElement(XmlContext context, Element element) {
        Attr codeNode = element.getAttributeNodeNS(ANDROID_URI, "versionCode");//$NON-NLS-1$
        if (codeNode != null && codeNode.getValue().startsWith(PREFIX_RESOURCE_REF)
                && context.isEnabled(ILLEGAL_REFERENCE)) {
            context.report(ILLEGAL_REFERENCE, element, context.getLocation(element),
                    "The android:versionCode cannot be a resource url, it must be "
                            + "a literal integer", null);
        } else if (codeNode == null && context.isEnabled(SET_VERSION)
                // Not required in Gradle projects; typically defined in build.gradle instead
                // and inserted at build time
                && !context.getMainProject().isGradleProject()) {
            context.report(SET_VERSION, element, context.getLocation(element),
                    "Should set android:versionCode to specify the application version", null);
        }
        Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, "versionName");//$NON-NLS-1$
        if (nameNode != null && nameNode.getValue().startsWith(PREFIX_RESOURCE_REF)
                && context.isEnabled(ILLEGAL_REFERENCE)) {
            context.report(ILLEGAL_REFERENCE, element, context.getLocation(element),
                    "The android:versionName cannot be a resource url, it must be "
                            + "a literal string", null);
        } else if (nameNode == null && context.isEnabled(SET_VERSION)
                // Not required in Gradle projects; typically defined in build.gradle instead
                // and inserted at build time
                && !context.getMainProject().isGradleProject()) {
            context.report(SET_VERSION, element, context.getLocation(element),
                    "Should set android:versionName to specify the application version", null);
        }
    }

    // ---- Implements Detector.XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_USES_PERMISSION,
                TAG_PERMISSION,
                "permission-tree",         //$NON-NLS-1$
                "permission-group",        //$NON-NLS-1$
                TAG_USES_SDK,
                "uses-configuration",      //$NON-NLS-1$
                TAG_USES_FEATURE,
                "supports-screens",        //$NON-NLS-1$
                "compatible-screens",      //$NON-NLS-1$
                "supports-gl-texture",     //$NON-NLS-1$
                TAG_USES_LIBRARY,
                TAG_ACTIVITY,
                TAG_SERVICE,
                TAG_PROVIDER,
                TAG_RECEIVER
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        Node parentNode = element.getParentNode();

        boolean isReceiver = tag.equals(TAG_RECEIVER);
        if (isReceiver) {
            checkDeviceAdmin(context, element);
        }

        if (tag.equals(TAG_USES_LIBRARY) || tag.equals(TAG_ACTIVITY) || tag.equals(TAG_SERVICE)
                || tag.equals(TAG_PROVIDER) || isReceiver) {
            if (!TAG_APPLICATION.equals(parentNode.getNodeName())
                    && context.isEnabled(WRONG_PARENT)) {
                context.report(WRONG_PARENT, element, context.getLocation(element),
                        String.format(
                        "The <%1$s> element must be a direct child of the <application> element",
                        tag), null);
            }

            if (tag.equals(TAG_ACTIVITY)) {
                Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                if (nameNode != null) {
                    String name = nameNode.getValue();
                    if (!name.isEmpty()) {
                        String pkg = context.getMainProject().getPackage();
                        if (name.charAt(0) == '.') {
                            name = pkg + name;
                        } else if (name.indexOf('.') == -1) {
                            name = pkg + '.' + name;
                        }
                        if (mActivities.contains(name)) {
                            String message = String.format(
                                    "Duplicate registration for activity %1$s", name);
                            context.report(DUPLICATE_ACTIVITY, element,
                                    context.getLocation(nameNode), message, null);
                        } else {
                            mActivities.add(name);
                        }
                    }
                }
            }

            return;
        }

        if (parentNode != element.getOwnerDocument().getDocumentElement()
                && context.isEnabled(WRONG_PARENT)) {
            context.report(WRONG_PARENT, element, context.getLocation(element),
                    String.format(
                    "The <%1$s> element must be a direct child of the " +
                    "<manifest> root element", tag), null);
        }

        if (tag.equals(TAG_USES_SDK)) {
            mSeenUsesSdk++;

            if (mSeenUsesSdk == 2) { // Only warn when we encounter the first one
                Location location = context.getLocation(element);

                // Link up *all* encountered locations in the document
                NodeList elements = element.getOwnerDocument().getElementsByTagName(TAG_USES_SDK);
                Location secondary = null;
                for (int i = elements.getLength() - 1; i >= 0; i--) {
                    Element e = (Element) elements.item(i);
                    if (e != element) {
                        Location l = context.getLocation(e);
                        l.setSecondary(secondary);
                        l.setMessage("Also appears here");
                        secondary = l;
                    }
                }
                location.setSecondary(secondary);

                if (context.isEnabled(MULTIPLE_USES_SDK)) {
                    context.report(MULTIPLE_USES_SDK, element, location,
                        "There should only be a single <uses-sdk> element in the manifest:" +
                        " merge these together", null);
                }
                return;
            }

            if (!element.hasAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)) {
                if (context.isEnabled(USES_SDK) && !context.getMainProject().isGradleProject()) {
                    context.report(USES_SDK, element, context.getLocation(element),
                        "<uses-sdk> tag should specify a minimum API level with " +
                        "android:minSdkVersion=\"?\"", null);
                }
            } else {
                Attr codeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION);
                if (codeNode != null && codeNode.getValue().startsWith(PREFIX_RESOURCE_REF)
                        && context.isEnabled(ILLEGAL_REFERENCE)) {
                    context.report(ILLEGAL_REFERENCE, element, context.getLocation(element),
                            "The android:minSdkVersion cannot be a resource url, it must be "
                                    + "a literal integer (or string if a preview codename)", null);
                }
            }

            if (!element.hasAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)) {
                // Warn if not setting target SDK -- but only if the min SDK is somewhat
                // old so there's some compatibility stuff kicking in (such as the menu
                // button etc)
                if (context.isEnabled(USES_SDK) && !context.getMainProject().isGradleProject()) {
                    context.report(USES_SDK, element, context.getLocation(element),
                        "<uses-sdk> tag should specify a target API level (the " +
                        "highest verified version; when running on later versions, " +
                        "compatibility behaviors may be enabled) with " +
                        "android:targetSdkVersion=\"?\"", null);
                }
            } else if (context.isEnabled(TARGET_NEWER)){
                String target = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
                try {
                    int api = Integer.parseInt(target);
                    if (api < context.getClient().getHighestKnownApiLevel()) {
                        context.report(TARGET_NEWER, element, context.getLocation(element),
                                "Not targeting the latest versions of Android; compatibility " +
                                "modes apply. Consider testing and updating this version. " +
                                "Consult the android.os.Build.VERSION_CODES javadoc for details.",
                                null);
                    }
                } catch (NumberFormatException nufe) {
                    // Ignore: AAPT will enforce this.
                }
            }

            Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
            if (nameNode != null && nameNode.getValue().startsWith(PREFIX_RESOURCE_REF)
                    && context.isEnabled(ILLEGAL_REFERENCE)) {
                context.report(ILLEGAL_REFERENCE, element, context.getLocation(element),
                        "The android:targetSdkVersion cannot be a resource url, it must be "
                                + "a literal integer (or string if a preview codename)", null);
            }
        }
        if (tag.equals(TAG_PERMISSION)) {
            Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (nameNode != null) {
                String name = nameNode.getValue();
                String base = name.substring(name.lastIndexOf('.') + 1);
                if (mPermissionNames == null) {
                    mPermissionNames = Maps.newHashMap();
                } else if (mPermissionNames.containsKey(base)) {
                    String prevName = mPermissionNames.get(base);
                    Location location = context.getLocation(nameNode);
                    NodeList siblings = element.getParentNode().getChildNodes();
                    for (int i = 0, n = siblings.getLength(); i < n; i++) {
                        Node node = siblings.item(i);
                        if (node == element) {
                            break;
                        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element sibling = (Element) node;
                            String suffix = '.' + base;
                            if (sibling.getTagName().equals(TAG_PERMISSION)) {
                                String b = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                                if (b.endsWith(suffix)) {
                                    Location prevLocation = context.getLocation(node);
                                    prevLocation.setMessage("Previous permission here");
                                    location.setSecondary(prevLocation);
                                    break;
                                }

                            }
                        }
                    }

                    String message = String.format("Permission name %1$s is not unique " +
                            "(appears in both %2$s and %3$s)", base, prevName, name);
                    context.report(UNIQUE_PERMISSION, element, location, message, null);
                }

                mPermissionNames.put(base, name);
            }
        }

        if (tag.equals(TAG_USES_PERMISSION)) {
            Attr name = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (name != null && name.getValue().equals(MOCK_LOCATION_PERMISSION)
                    && context.getMainProject().isGradleProject()
                    && !isDebugManifest(context, context.file)) {
                String message = "Mock locations should only be requested in a debug-specific "
                        + "manifest file (typically src/debug/AndroidManifest.xml)";
                Location location = context.getLocation(name);
                context.report(MOCK_LOCATION, element, location, message, null);
            }
        }

        if (tag.equals(TAG_APPLICATION)) {
            mSeenApplication = true;
            if (!element.hasAttributeNS(ANDROID_URI, SdkConstants.ATTR_ALLOW_BACKUP)
                    && context.isEnabled(ALLOW_BACKUP)
                    && context.getMainProject().getMinSdk() >= 4) {
                // TODO: For gradle, just needs to be set SOMEWHERE
                context.report(ALLOW_BACKUP, element, context.getLocation(element),
                            "Should explicitly set android:allowBackup to true or " +
                            "false (it's true by default, and that can have some security " +
                            "implications for the application's data)", null);
            }

            if (!element.hasAttributeNS(ANDROID_URI, SdkConstants.ATTR_ICON)
                    && !context.getProject().isLibrary()) {
                context.report(APPLICATION_ICON, element, context.getLocation(element),
                            "Should explicitly set android:icon, there is no default", null);
            }
        } else if (mSeenApplication) {
            if (context.isEnabled(ORDER)) {
                context.report(ORDER, element, context.getLocation(element),
                    String.format("<%1$s> tag appears after <application> tag", tag), null);
            }

            // Don't complain for *every* element following the <application> tag
            mSeenApplication = false;
        }

        if (tag.equals(TAG_USES_FEATURE)) {
            Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (nameNode != null) {
                String name = nameNode.getValue();
                if (!name.isEmpty()) {
                    if (mUsesFeatures.contains(name)) {
                        String message = String.format(
                                "Duplicate declaration of uses-feature %1$s", name);
                        context.report(DUPLICATE_USES_FEATURE, element,
                                context.getLocation(nameNode), message, null);
                    } else {
                        mUsesFeatures.add(name);
                    }
                }
            }
        }
    }

    /** Returns true iff the given manifest file is in a debug-specific source set */
    private static boolean isDebugManifest(XmlContext context, File manifestFile) {
        AndroidProject model = context.getProject().getGradleProjectModel();
        if (model != null) {
            for (BuildTypeContainer container : model.getBuildTypes()) {
                if (container.getBuildType().isDebuggable()) {
                    if (manifestFile.equals(container.getSourceProvider().getManifestFile())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void checkDeviceAdmin(XmlContext context, Element element) {
        List<Element> children = LintUtils.getChildren(element);
        boolean requiredIntentFilterFound = false;
        boolean deviceAdmin = false;
        Attr locationNode = null;
        for (Element child : children) {
            String tagName = child.getTagName();
            if (tagName.equals(TAG_INTENT_FILTER) && !requiredIntentFilterFound) {
                boolean dataFound = false;
                boolean actionFound = false;
                for (Element filterChild : LintUtils.getChildren(child)) {
                    String filterTag = filterChild.getTagName();
                    if (filterTag.equals(NODE_ACTION)) {
                        String name = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if ("android.app.action.DEVICE_ADMIN_ENABLED".equals(name)) { //$NON-NLS-1$
                            actionFound = true;
                        }
                    } else if (filterTag.equals(NODE_DATA)) {
                        dataFound = true;
                    }
                }
                if (actionFound && !dataFound) {
                    requiredIntentFilterFound = true;
                }
            } else if (tagName.equals(NODE_METADATA)) {
                Attr valueNode = child.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                if (valueNode != null) {
                    String name = valueNode.getValue();
                    if ("android.app.device_admin".equals(name)) { //$NON-NLS-1$
                        deviceAdmin = true;
                        locationNode = valueNode;
                    }
                }
            }
        }

        if (deviceAdmin && !requiredIntentFilterFound && context.isEnabled(DEVICE_ADMIN)) {
            context.report(DEVICE_ADMIN, locationNode, context.getLocation(locationNode),
                "You must have an intent filter for action "
                        + "android.app.action.DEVICE_ADMIN_ENABLED",
                null);
        }
    }
}
