/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Project;

@SuppressWarnings("javadoc")
public class ApiDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ApiDetector();
    }

    public void testXmlApi1() throws Exception {
        assertEquals(
            "res/color/colors.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n" +
            "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n" +
            "                                                ^\n" +
            "res/layout/layout.xml:9: Error: View requires API level 5 (current min is 1): <QuickContactBadge> [NewApi]\n" +
            "    <QuickContactBadge\n" +
            "    ^\n" +
            "res/layout/layout.xml:15: Error: View requires API level 11 (current min is 1): <CalendarView> [NewApi]\n" +
            "    <CalendarView\n" +
            "    ^\n" +
            "res/layout/layout.xml:21: Error: View requires API level 14 (current min is 1): <GridLayout> [NewApi]\n" +
            "    <GridLayout\n" +
            "    ^\n" +
            "res/layout/layout.xml:22: Error: @android:attr/actionBarSplitStyle requires API level 14 (current min is 1) [NewApi]\n" +
            "        foo=\"@android:attr/actionBarSplitStyle\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/layout.xml:23: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n" +
            "        bar=\"@android:color/holo_red_light\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/themes.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n" +
            "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n" +
            "                                                ^\n" +
            "7 errors, 0 warnings\n" +
            "",

            lintProject(
                "apicheck/minsdk1.xml=>AndroidManifest.xml",
                "apicheck/layout.xml=>res/layout/layout.xml",
                "apicheck/themes.xml=>res/values/themes.xml",
                "apicheck/themes.xml=>res/color/colors.xml"
                ));
    }

    public void testAttrWithoutSlash() throws Exception {
        assertEquals(""
                + "res/layout/divider.xml:7: Error: ?android:dividerHorizontal requires API level 11 (current min is 1) [NewApi]\n"
                + "    android:divider=\"?android:dividerHorizontal\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        "apicheck/minsdk1.xml=>AndroidManifest.xml",
                        "apicheck/divider.xml=>res/layout/divider.xml"
                ));
    }

    public void testXmlApi14() throws Exception {
        assertEquals(
                "No warnings.",

                lintProject(
                    "apicheck/minsdk14.xml=>AndroidManifest.xml",
                    "apicheck/layout.xml=>res/layout/layout.xml",
                    "apicheck/themes.xml=>res/values/themes.xml",
                    "apicheck/themes.xml=>res/color/colors.xml"
                    ));
    }

    public void testXmlApiIceCreamSandwich() throws Exception {
        assertEquals(
                "No warnings.",

                lintProject(
                        "apicheck/minics.xml=>AndroidManifest.xml",
                        "apicheck/layout.xml=>res/layout/layout.xml",
                        "apicheck/themes.xml=>res/values/themes.xml",
                        "apicheck/themes.xml=>res/color/colors.xml"
                ));
    }

    public void testXmlApi1TargetApi() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                "apicheck/minsdk1.xml=>AndroidManifest.xml",
                "apicheck/layout_targetapi.xml=>res/layout/layout.xml"
                ));
    }

    public void testXmlApiFolderVersion11() throws Exception {
        assertEquals(
            "res/color-v11/colors.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n" +
            "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n" +
            "                                                ^\n" +
            "res/layout-v11/layout.xml:21: Error: View requires API level 14 (current min is 1): <GridLayout> [NewApi]\n" +
            "    <GridLayout\n" +
            "    ^\n" +
            "res/layout-v11/layout.xml:22: Error: @android:attr/actionBarSplitStyle requires API level 14 (current min is 1) [NewApi]\n" +
            "        foo=\"@android:attr/actionBarSplitStyle\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout-v11/layout.xml:23: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n" +
            "        bar=\"@android:color/holo_red_light\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values-v11/themes.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n" +
            "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n" +
            "                                                ^\n" +
            "5 errors, 0 warnings\n" +
            "",

            lintProject(
                "apicheck/minsdk1.xml=>AndroidManifest.xml",
                "apicheck/layout.xml=>res/layout-v11/layout.xml",
                "apicheck/themes.xml=>res/values-v11/themes.xml",
                "apicheck/themes.xml=>res/color-v11/colors.xml"
                ));
    }

    public void testXmlApiFolderVersion14() throws Exception {
        assertEquals(
                "No warnings.",

                lintProject(
                    "apicheck/minsdk1.xml=>AndroidManifest.xml",
                    "apicheck/layout.xml=>res/layout-v14/layout.xml",
                    "apicheck/themes.xml=>res/values-v14/themes.xml",
                    "apicheck/themes.xml=>res/color-v14/colors.xml"
                    ));
    }

    public void testApi1() throws Exception {
        assertEquals(
            "src/foo/bar/ApiCallTest.java:20: Error: Call requires API level 11 (current min is 1): android.app.Activity#getActionBar [NewApi]\n" +
            "  getActionBar(); // API 11\n" +
            "  ~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:24: Error: Class requires API level 8 (current min is 1): org.w3c.dom.DOMErrorHandler [NewApi]\n" +
            "  Class<?> clz = DOMErrorHandler.class; // API 8\n" +
            "                 ~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:27: Error: Call requires API level 3 (current min is 1): android.widget.Chronometer#getOnChronometerTickListener [NewApi]\n" +
            "  chronometer.getOnChronometerTickListener(); // API 3 \n" +
            "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 1): android.widget.Chronometer#setTextIsSelectable [NewApi]\n" +
            "  chronometer.setTextIsSelectable(true); // API 11\n" +
            "              ~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:33: Error: Field requires API level 11 (current min is 1): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n" +
            "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n" +
            "                         ~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 1): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n" +
            "  BatteryInfo batteryInfo = getReport().batteryInfo;\n" +
            "              ~~~~~~~~~~~\n" +
            //             Note: the above error range is wrong; should be pointing to the second
            "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n" +
            "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n" +
            "                              ~~~~~~~\n" +
            "7 errors, 0 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk1.xml=>AndroidManifest.xml",
                "apicheck/ApiCallTest.java.txt=>src/foo/bar/ApiCallTest.java",
                "apicheck/ApiCallTest.class.data=>bin/classes/foo/bar/ApiCallTest.class"
                ));
    }

    public void testApi2() throws Exception {
        assertEquals(
            "src/foo/bar/ApiCallTest.java:20: Error: Call requires API level 11 (current min is 2): android.app.Activity#getActionBar [NewApi]\n" +
            "  getActionBar(); // API 11\n" +
            "  ~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:24: Error: Class requires API level 8 (current min is 2): org.w3c.dom.DOMErrorHandler [NewApi]\n" +
            "  Class<?> clz = DOMErrorHandler.class; // API 8\n" +
            "                 ~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:27: Error: Call requires API level 3 (current min is 2): android.widget.Chronometer#getOnChronometerTickListener [NewApi]\n" +
            "  chronometer.getOnChronometerTickListener(); // API 3 \n" +
            "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 2): android.widget.Chronometer#setTextIsSelectable [NewApi]\n" +
            "  chronometer.setTextIsSelectable(true); // API 11\n" +
            "              ~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:33: Error: Field requires API level 11 (current min is 2): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n" +
            "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n" +
            "                         ~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 2): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n" +
            "  BatteryInfo batteryInfo = getReport().batteryInfo;\n" +
            "              ~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 2): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n" +
            "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n" +
            "                              ~~~~~~~\n" +
            "7 errors, 0 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk2.xml=>AndroidManifest.xml",
                "apicheck/ApiCallTest.java.txt=>src/foo/bar/ApiCallTest.java",
                "apicheck/ApiCallTest.class.data=>bin/classes/foo/bar/ApiCallTest.class"
                ));
    }

    public void testApi4() throws Exception {
        assertEquals(
            "src/foo/bar/ApiCallTest.java:20: Error: Call requires API level 11 (current min is 4): android.app.Activity#getActionBar [NewApi]\n" +
            "  getActionBar(); // API 11\n" +
            "  ~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:24: Error: Class requires API level 8 (current min is 4): org.w3c.dom.DOMErrorHandler [NewApi]\n" +
            "  Class<?> clz = DOMErrorHandler.class; // API 8\n" +
            "                 ~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 4): android.widget.Chronometer#setTextIsSelectable [NewApi]\n" +
            "  chronometer.setTextIsSelectable(true); // API 11\n" +
            "              ~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:33: Error: Field requires API level 11 (current min is 4): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n" +
            "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n" +
            "                         ~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 4): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n" +
            "  BatteryInfo batteryInfo = getReport().batteryInfo;\n" +
            "              ~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 4): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n" +
            "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n" +
            "                              ~~~~~~~\n" +
            "6 errors, 0 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk4.xml=>AndroidManifest.xml",
                "apicheck/ApiCallTest.java.txt=>src/foo/bar/ApiCallTest.java",
                "apicheck/ApiCallTest.class.data=>bin/classes/foo/bar/ApiCallTest.class"
                ));
    }

    public void testApi10() throws Exception {
        assertEquals(
            "src/foo/bar/ApiCallTest.java:20: Error: Call requires API level 11 (current min is 10): android.app.Activity#getActionBar [NewApi]\n" +
            "  getActionBar(); // API 11\n" +
            "  ~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 10): android.widget.Chronometer#setTextIsSelectable [NewApi]\n" +
            "  chronometer.setTextIsSelectable(true); // API 11\n" +
            "              ~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:33: Error: Field requires API level 11 (current min is 10): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n" +
            "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n" +
            "                         ~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 10): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n" +
            "  BatteryInfo batteryInfo = getReport().batteryInfo;\n" +
            "              ~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 10): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n" +
            "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n" +
            "                              ~~~~~~~\n" +
            "5 errors, 0 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk10.xml=>AndroidManifest.xml",
                "apicheck/ApiCallTest.java.txt=>src/foo/bar/ApiCallTest.java",
                "apicheck/ApiCallTest.class.data=>bin/classes/foo/bar/ApiCallTest.class"
                ));
        }

    public void testApi14() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk14.xml=>AndroidManifest.xml",
                "apicheck/ApiCallTest.java.txt=>src/foo/bar/ApiCallTest.java",
                "apicheck/ApiCallTest.class.data=>bin/classes/foo/bar/ApiCallTest.class"
                ));
    }

    public void testInheritStatic() throws Exception {
        assertEquals(
            "src/foo/bar/ApiCallTest5.java:16: Error: Call requires API level 11 (current min is 2): android.view.View#resolveSizeAndState [NewApi]\n" +
            "        int measuredWidth = View.resolveSizeAndState(widthMeasureSpec,\n" +
            "                                 ~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest5.java:18: Error: Call requires API level 11 (current min is 2): android.view.View#resolveSizeAndState [NewApi]\n" +
            "        int measuredHeight = resolveSizeAndState(heightMeasureSpec,\n" +
            "                             ~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest5.java:20: Error: Call requires API level 11 (current min is 2): android.view.View#combineMeasuredStates [NewApi]\n" +
            "        View.combineMeasuredStates(0, 0);\n" +
            "             ~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiCallTest5.java:21: Error: Call requires API level 11 (current min is 2): android.view.View#combineMeasuredStates [NewApi]\n" +
            "        ApiCallTest5.combineMeasuredStates(0, 0);\n" +
            "                     ~~~~~~~~~~~~~~~~~~~~~\n" +
            "4 errors, 0 warnings\n" +
            "",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk2.xml=>AndroidManifest.xml",
                "apicheck/ApiCallTest5.java.txt=>src/foo/bar/ApiCallTest5.java",
                "apicheck/ApiCallTest5.class.data=>bin/classes/foo/bar/ApiCallTest5.class"
                ));
    }

    public void testInheritLocal() throws Exception {
        // Test virtual dispatch in a local class which extends some other local class (which
        // in turn extends an Android API)
        assertEquals(
            "src/test/pkg/ApiCallTest3.java:10: Error: Call requires API level 11 (current min is 1): android.app.Activity#getActionBar [NewApi]\n" +
            "  getActionBar(); // API 11\n" +
            "  ~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n" +
            "",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk1.xml=>AndroidManifest.xml",
                "apicheck/Intermediate.java.txt=>src/test/pkg/Intermediate.java",
                "apicheck/ApiCallTest3.java.txt=>src/test/pkg/ApiCallTest3.java",
                "apicheck/ApiCallTest3.class.data=>bin/classes/test/pkg/ApiCallTest3.class",
                "apicheck/Intermediate.class.data=>bin/classes/test/pkg/Intermediate.class"
                ));
    }

    public void testViewClassLayoutReference() throws Exception {
        assertEquals(
            "res/layout/view.xml:9: Error: View requires API level 5 (current min is 1): <QuickContactBadge> [NewApi]\n" +
            "    <view\n" +
            "    ^\n" +
            "res/layout/view.xml:16: Error: View requires API level 11 (current min is 1): <CalendarView> [NewApi]\n" +
            "    <view\n" +
            "    ^\n" +
            "res/layout/view.xml:24: Error: ?android:attr/dividerHorizontal requires API level 11 (current min is 1) [NewApi]\n" +
            "        unknown=\"?android:attr/dividerHorizontal\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/view.xml:25: Error: ?android:attr/textColorLinkInverse requires API level 11 (current min is 1) [NewApi]\n" +
            "        android:textColor=\"?android:attr/textColorLinkInverse\" />\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "4 errors, 0 warnings\n" +
            "",

            lintProject(
                    "apicheck/minsdk1.xml=>AndroidManifest.xml",
                    "apicheck/view.xml=>res/layout/view.xml"
                ));
    }

    public void testIOException() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=35190
        assertEquals(
            "src/test/pkg/ApiCallTest6.java:8: Error: Call requires API level 9 (current min is 1): new java.io.IOException [NewApi]\n" +
            "        IOException ioException = new IOException(throwable);\n" +
            "        ~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk1.xml=>AndroidManifest.xml",
                    "apicheck/Intermediate.java.txt=>src/test/pkg/Intermediate.java",
                    "apicheck/ApiCallTest6.java.txt=>src/test/pkg/ApiCallTest6.java",
                    "apicheck/ApiCallTest6.class.data=>bin/classes/test/pkg/ApiCallTest6.class"
                ));
    }


    // Test suppressing errors -- on classes, methods etc.

    public void testSuppress() throws Exception {
        assertEquals(
            // These errors are correctly -not- suppressed because they
            // appear in method3 (line 74-98) which is annotated with a
            // @SuppressLint annotation specifying only an unrelated issue id

            "src/foo/bar/SuppressTest1.java:76: Error: Call requires API level 11 (current min is 1): android.app.Activity#getActionBar [NewApi]\n" +
            "  getActionBar(); // API 11\n" +
            "  ~~~~~~~~~~~~\n" +
            "src/foo/bar/SuppressTest1.java:80: Error: Class requires API level 8 (current min is 1): org.w3c.dom.DOMErrorHandler [NewApi]\n" +
            "  Class<?> clz = DOMErrorHandler.class; // API 8\n" +
            "                 ~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/SuppressTest1.java:83: Error: Call requires API level 3 (current min is 1): android.widget.Chronometer#getOnChronometerTickListener [NewApi]\n" +
            "  chronometer.getOnChronometerTickListener(); // API 3\n" +
            "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/SuppressTest1.java:86: Error: Call requires API level 11 (current min is 1): android.widget.Chronometer#setTextIsSelectable [NewApi]\n" +
            "  chronometer.setTextIsSelectable(true); // API 11\n" +
            "              ~~~~~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/SuppressTest1.java:89: Error: Field requires API level 11 (current min is 1): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n" +
            "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n" +
            "                         ~~~~~~~~~~~~~\n" +
            "src/foo/bar/SuppressTest1.java:94: Error: Field requires API level 14 (current min is 1): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n" +
            "  BatteryInfo batteryInfo = getReport().batteryInfo;\n" +
            "              ~~~~~~~~~~~\n" +
            "src/foo/bar/SuppressTest1.java:97: Error: Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n" +
            "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n" +
            "                              ~~~~~~~\n" +

            // Note: These annotations are within the methods, not ON the methods, so they have
            // no effect (because they don't end up in the bytecode)


            "src/foo/bar/SuppressTest4.java:19: Error: Field requires API level 14 (current min is 1): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n" +
            "  BatteryInfo batteryInfo = report.batteryInfo;\n" +
            "              ~~~~~~~~~~~\n" +
            "8 errors, 0 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk1.xml=>AndroidManifest.xml",
                "apicheck/SuppressTest1.java.txt=>src/foo/bar/SuppressTest1.java",
                "apicheck/SuppressTest1.class.data=>bin/classes/foo/bar/SuppressTest1.class",
                "apicheck/SuppressTest2.java.txt=>src/foo/bar/SuppressTest2.java",
                "apicheck/SuppressTest2.class.data=>bin/classes/foo/bar/SuppressTest2.class",
                "apicheck/SuppressTest3.java.txt=>src/foo/bar/SuppressTest3.java",
                "apicheck/SuppressTest3.class.data=>bin/classes/foo/bar/SuppressTest3.class",
                "apicheck/SuppressTest4.java.txt=>src/foo/bar/SuppressTest4.java",
                "apicheck/SuppressTest4.class.data=>bin/classes/foo/bar/SuppressTest4.class"
                ));
    }

    public void testSuppressInnerClasses() throws Exception {
        assertEquals(
            // These errors are correctly -not- suppressed because they
            // appear outside the middle inner class suppressing its own errors
            // and its child's errors
            "src/test/pkg/ApiCallTest4.java:9: Error: Call requires API level 14 (current min is 1): new android.widget.GridLayout [NewApi]\n" +
            "        new GridLayout(null, null, 0);\n" +
            "            ~~~~~~~~~~\n" +
            "src/test/pkg/ApiCallTest4.java:38: Error: Call requires API level 14 (current min is 1): new android.widget.GridLayout [NewApi]\n" +
            "            new GridLayout(null, null, 0);\n" +
            "                ~~~~~~~~~~\n" +
            "2 errors, 0 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk1.xml=>AndroidManifest.xml",
                "apicheck/ApiCallTest4.java.txt=>src/test/pkg/ApiCallTest4.java",
                "apicheck/ApiCallTest4.class.data=>bin/classes/test/pkg/ApiCallTest4.class",
                "apicheck/ApiCallTest4$1.class.data=>bin/classes/test/pkg/ApiCallTest4$1.class",
                "apicheck/ApiCallTest4$InnerClass1.class.data=>bin/classes/test/pkg/ApiCallTest4$InnerClass1.class",
                "apicheck/ApiCallTest4$InnerClass2.class.data=>bin/classes/test/pkg/ApiCallTest4$InnerClass2.class",
                "apicheck/ApiCallTest4$InnerClass1$InnerInnerClass1.class.data=>bin/classes/test/pkg/ApiCallTest4$InnerClass1$InnerInnerClass1.class"
                ));
    }

    public void testApiTargetAnnotation() throws Exception {
        assertEquals(
            "src/foo/bar/ApiTargetTest.java:13: Error: Class requires API level 8 (current min is 1): org.w3c.dom.DOMErrorHandler [NewApi]\n" +
            "  Class<?> clz = DOMErrorHandler.class; // API 8\n" +
            "                 ~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiTargetTest.java:25: Error: Class requires API level 8 (current min is 4): org.w3c.dom.DOMErrorHandler [NewApi]\n" +
            "  Class<?> clz = DOMErrorHandler.class; // API 8\n" +
            "                 ~~~~~~~~~~~~~~~\n" +
            "src/foo/bar/ApiTargetTest.java:39: Error: Class requires API level 8 (current min is 7): org.w3c.dom.DOMErrorHandler [NewApi]\n" +
            "   Class<?> clz = DOMErrorHandler.class; // API 8\n" +
            "                  ~~~~~~~~~~~~~~~\n" +
            "3 errors, 0 warnings\n" +
            "",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk1.xml=>AndroidManifest.xml",
                "apicheck/ApiTargetTest.java.txt=>src/foo/bar/ApiTargetTest.java",
                "apicheck/ApiTargetTest.class.data=>bin/classes/foo/bar/ApiTargetTest.class",
                "apicheck/ApiTargetTest$LocalClass.class.data=>bin/classes/foo/bar/ApiTargetTest$LocalClass.class"
                ));
    }

    public void testTargetAnnotationInner() throws Exception {
        assertEquals(
            "src/test/pkg/ApiTargetTest2.java:32: Error: Call requires API level 14 (current min is 3): new android.widget.GridLayout [NewApi]\n" +
            "                        new GridLayout(null, null, 0);\n" +
            "                            ~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk1.xml=>AndroidManifest.xml",
                "apicheck/ApiTargetTest2.java.txt=>src/test/pkg/ApiTargetTest2.java",
                "apicheck/ApiTargetTest2.class.data=>bin/classes/test/pkg/ApiTargetTest2.class",
                "apicheck/ApiTargetTest2$1.class.data=>bin/classes/test/pkg/ApiTargetTest2$1.class",
                "apicheck/ApiTargetTest2$1$2.class.data=>bin/classes/test/pkg/ApiTargetTest2$1$2.class",
                "apicheck/ApiTargetTest2$1$1.class.data=>bin/classes/test/pkg/ApiTargetTest2$1$1.class"
                ));
    }

    public void testSuper() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=36384
        assertEquals(
            "src/test/pkg/ApiCallTest7.java:8: Error: Call requires API level 9 (current min is 4): new java.io.IOException [NewApi]\n" +
            "        super(message, cause); // API 9\n" +
            "        ~~~~~\n" +
            "src/test/pkg/ApiCallTest7.java:12: Error: Call requires API level 9 (current min is 4): new java.io.IOException [NewApi]\n" +
            "        super.toString(); throw new IOException((Throwable) null); // API 9\n" +
            "                                    ~~~~~~~~~~~\n" +
            "2 errors, 0 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "apicheck/ApiCallTest7.java.txt=>src/test/pkg/ApiCallTest7.java",
                    "apicheck/ApiCallTest7.class.data=>bin/classes/test/pkg/ApiCallTest7.class"
                ));
    }

    public void testEnums() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=36951
        assertEquals(
            "src/test/pkg/TestEnum.java:26: Error: Enum value requires API level 11 (current min is 4): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n" +
            "            case OVERLAY: {\n" +
            "                 ~~~~~~~\n" +
            "src/test/pkg/TestEnum.java:37: Error: Enum value requires API level 11 (current min is 4): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n" +
            "            case OVERLAY: {\n" +
            "                 ~~~~~~~\n" +
            "src/test/pkg/TestEnum.java:61: Error: Enum for switch requires API level 11 (current min is 4): android.renderscript.Element.DataType [NewApi]\n" +
            "        switch (type) {\n" +
            "        ^\n" +
            "3 errors, 0 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "apicheck/TestEnum.java.txt=>src/test/pkg/TestEnum.java",
                    "apicheck/TestEnum.class.data=>bin/classes/test/pkg/TestEnum.class"
                ));
    }

    @Override
    public String getSuperClass(Project project, String name) {
        // For testInterfaceInheritance
        if (name.equals("android/database/sqlite/SQLiteStatement")) {
            return "android/database/sqlite/SQLiteProgram";
        } else if (name.equals("android/database/sqlite/SQLiteProgram")) {
            return "android/database/sqlite/SQLiteClosable";
        } else if (name.equals("android/database/sqlite/SQLiteClosable")) {
            return "java/lang/Object";
        }
        return null;
    }

    public void testInterfaceInheritance() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=38004
        assertEquals(
            "No warnings.",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "apicheck/CloseTest.java.txt=>src/test/pkg/CloseTest.java",
                    "apicheck/CloseTest.class.data=>bin/classes/test/pkg/CloseTest.class"
                ));
    }

    public void testInnerClassPositions() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=38113
        assertEquals(
            "No warnings.",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "apicheck/ApiCallTest8.java.txt=>src/test/pkg/ApiCallTest8.java",
                    "apicheck/ApiCallTest8.class.data=>bin/classes/test/pkg/ApiCallTest8.class"
                ));
    }

    public void testManifestReferences() throws Exception {
        assertEquals(
            "AndroidManifest.xml:15: Error: @android:style/Theme.Holo requires API level 11 (current min is 4) [NewApi]\n" +
            "            android:theme=\"@android:style/Theme.Holo\" >\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/holomanifest.xml=>AndroidManifest.xml"
                ));
    }

    public void testSuppressFieldAnnotations() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=38626
        assertEquals(
            "src/test/pkg/ApiCallTest9.java:9: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n" +
            "    private GridLayout field1 = new GridLayout(null);\n" +
            "            ~~~~~~~~~~\n" +
            "src/test/pkg/ApiCallTest9.java:12: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n" +
            "    private static GridLayout field2 = new GridLayout(null);\n" +
            "                   ~~~~~~~~~~\n" +
            "2 errors, 0 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "apicheck/ApiCallTest9.java.txt=>src/test/pkg/ApiCallTest9.java",
                    "apicheck/ApiCallTest9.class.data=>bin/classes/test/pkg/ApiCallTest9.class"
                ));
    }

    public void test38195() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=38195
        assertEquals(
            "bin/classes/TestLint.class: Error: Call requires API level 16 (current min is 4): new android.database.SQLException [NewApi]\n" +
            "bin/classes/TestLint.class: Error: Call requires API level 9 (current min is 4): java.lang.String#isEmpty [NewApi]\n" +
            "bin/classes/TestLint.class: Error: Call requires API level 9 (current min is 4): new java.sql.SQLException [NewApi]\n" +
            "3 errors, 0 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    /*
                        Compiled from "TestLint.java"
                        public class test.pkg.TestLint extends java.lang.Object{
                        public test.pkg.TestLint();
                          Code:
                           0:   aload_0
                           1:   invokespecial   #8; //Method java/lang/Object."<init>":()V
                           4:   return

                        public void test(java.lang.Exception)   throws java.lang.Exception;
                          Code:
                           0:   ldc #19; //String
                           2:   invokevirtual   #21; //Method java/lang/String.isEmpty:()Z
                           5:   istore_2
                           6:   new #27; //class java/sql/SQLException
                           9:   dup
                           10:  ldc #29; //String error on upgrade:
                           12:  aload_1
                           13:  invokespecial   #31; //Method java/sql/SQLException."<init>":
                                                       (Ljava/lang/String;Ljava/lang/Throwable;)V
                           16:  athrow

                        public void test2(java.lang.Exception)   throws java.lang.Exception;
                          Code:
                           0:   new #39; //class android/database/SQLException
                           3:   dup
                           4:   ldc #29; //String error on upgrade:
                           6:   aload_1
                           7:   invokespecial   #41; //Method android/database/SQLException.
                                               "<init>":(Ljava/lang/String;Ljava/lang/Throwable;)V
                           10:  athrow
                        }
                     */
                    "apicheck/TestLint.class.data=>bin/classes/TestLint.class"
                ));
    }

    public void testAllowLocalMethodsImplementingInaccessible() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=39030
        assertEquals(
            "src/test/pkg/ApiCallTest10.java:25: Error: Call requires API level 14 (current min is 4): android.view.View#onPopulateAccessibilityEvent [NewApi]\n" +
            "        super.onPopulateAccessibilityEvent(event); // Valid lint warning\n" +
            "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ApiCallTest10.java:31: Error: Call requires API level 14 (current min is 4): android.view.View#dispatchGenericFocusedEvent [NewApi]\n" +
            "        return super.dispatchGenericFocusedEvent(event); // Should flag this\n" +
            "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ApiCallTest10.java:40: Error: Call requires API level 14 (current min is 4): android.view.View#dispatchHoverEvent [NewApi]\n" +
            "        dispatchHoverEvent(null);\n" +
            "        ~~~~~~~~~~~~~~~~~~\n" +
            "3 errors, 0 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "apicheck/ApiCallTest10.java.txt=>src/test/pkg/ApiCallTest10.java",
                    "apicheck/ApiCallTest10.class.data=>bin/classes/test/pkg/ApiCallTest10.class"
                ));
    }

    public void testOverrideUnknownTarget() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "apicheck/ApiCallTest11.java.txt=>src/test/pkg/ApiCallTest11.java",
                    "apicheck/ApiCallTest11.class.data=>bin/classes/test/pkg/ApiCallTest11.class"
                ));
    }

    public void testOverride() throws Exception {
        assertEquals(
            "src/test/pkg/ApiCallTest11.java:13: Error: This method is not overriding anything with the current build target, but will in API level 11 (current target is 3): test.pkg.ApiCallTest11#getActionBar [Override]\n" +
            "    public ActionBar getActionBar() {\n" +
            "                     ~~~~~~~~~~~~\n" +
            "src/test/pkg/ApiCallTest11.java:17: Error: This method is not overriding anything with the current build target, but will in API level 17 (current target is 3): test.pkg.ApiCallTest11#isDestroyed [Override]\n" +
            "    public boolean isDestroyed() {\n" +
            "                   ~~~~~~~~~~~\n" +
            "src/test/pkg/ApiCallTest11.java:39: Error: This method is not overriding anything with the current build target, but will in API level 11 (current target is 3): test.pkg.ApiCallTest11.MyLinear#setDividerDrawable [Override]\n" +
            "        public void setDividerDrawable(Drawable dividerDrawable) {\n" +
            "                    ~~~~~~~~~~~~~~~~~~\n" +
            "3 errors, 0 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "project.properties1=>project.properties",
                    "apicheck/ApiCallTest11.java.txt=>src/test/pkg/ApiCallTest11.java",
                    "apicheck/ApiCallTest11.class.data=>bin/classes/test/pkg/ApiCallTest11.class",
                    "apicheck/ApiCallTest11$MyLinear.class.data=>bin/classes/test/pkg/ApiCallTest11$MyLinear.class",
                    "apicheck/ApiCallTest11$MyActivity.class.data=>bin/classes/test/pkg/ApiCallTest11$MyActivity.class"
                ));
    }

    public void testDateFormat() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=40876
        assertEquals(
            "src/test/pkg/ApiCallTest12.java:18: Error: Call requires API level 9 (current min is 4): java.text.DateFormatSymbols#getInstance [NewApi]\n" +
            "  new SimpleDateFormat(\"yyyy-MM-dd\", DateFormatSymbols.getInstance());\n" +
            "                                                       ~~~~~~~~~~~\n" +
            "src/test/pkg/ApiCallTest12.java:23: Error: The pattern character 'L' requires API level 9 (current min is 4) : \"yyyy-MM-dd LL\" [NewApi]\n" +
            "  new SimpleDateFormat(\"yyyy-MM-dd LL\", Locale.US);\n" +
            "                        ^\n" +
            "src/test/pkg/ApiCallTest12.java:25: Error: The pattern character 'c' requires API level 9 (current min is 4) : \"cc yyyy-MM-dd\" [NewApi]\n" +
            "  SimpleDateFormat format = new SimpleDateFormat(\"cc yyyy-MM-dd\");\n" +
            "                                                  ^\n" +
            "3 errors, 0 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "project.properties1=>project.properties",
                    "apicheck/ApiCallTest12.java.txt=>src/test/pkg/ApiCallTest12.java",
                    "apicheck/ApiCallTest12.class.data=>bin/classes/test/pkg/ApiCallTest12.class"
                ));
    }

    public void testDateFormatOk() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk10.xml=>AndroidManifest.xml",
                    "project.properties1=>project.properties",
                    "apicheck/ApiCallTest12.java.txt=>src/test/pkg/ApiCallTest12.java",
                    "apicheck/ApiCallTest12.class.data=>bin/classes/test/pkg/ApiCallTest12.class"
                ));
    }

    public void testJavaConstants() throws Exception {
        assertEquals(""
                + "src/test/pkg/ApiSourceCheck.java:5: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                + "import static android.view.View.MEASURED_STATE_MASK;\n"
                + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:30: Warning: Field requires API level 11 (current min is 1): android.widget.ZoomControls#MEASURED_STATE_MASK [InlinedApi]\n"
                + "        int x = MEASURED_STATE_MASK;\n"
                + "                ~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:33: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                + "        int y = android.view.View.MEASURED_STATE_MASK;\n"
                + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:36: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                + "        int z = View.MEASURED_STATE_MASK;\n"
                + "                ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:37: Warning: Field requires API level 14 (current min is 1): android.view.View#FIND_VIEWS_WITH_TEXT [InlinedApi]\n"
                + "        int find2 = View.FIND_VIEWS_WITH_TEXT; // requires API 14\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:40: Warning: Field requires API level 12 (current min is 1): android.app.ActivityManager#MOVE_TASK_NO_USER_ACTION [InlinedApi]\n"
                + "        int w = ActivityManager.MOVE_TASK_NO_USER_ACTION;\n"
                + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:41: Warning: Field requires API level 14 (current min is 1): android.widget.ZoomButton#FIND_VIEWS_WITH_CONTENT_DESCRIPTION [InlinedApi]\n"
                + "        int find1 = ZoomButton.FIND_VIEWS_WITH_CONTENT_DESCRIPTION; // requires\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:44: Warning: Field requires API level 9 (current min is 1): android.widget.ZoomControls#OVER_SCROLL_ALWAYS [InlinedApi]\n"
                + "        int overScroll = OVER_SCROLL_ALWAYS; // requires API 9\n"
                + "                         ~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:47: Warning: Field requires API level 16 (current min is 1): android.widget.ZoomControls#IMPORTANT_FOR_ACCESSIBILITY_AUTO [InlinedApi]\n"
                + "        int auto = IMPORTANT_FOR_ACCESSIBILITY_AUTO; // requires API 16\n"
                + "                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:54: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                + "        return (child.getMeasuredWidth() & View.MEASURED_STATE_MASK)\n"
                + "                                           ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:55: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_HEIGHT_STATE_SHIFT [InlinedApi]\n"
                + "                | ((child.getMeasuredHeight() >> View.MEASURED_HEIGHT_STATE_SHIFT) & (View.MEASURED_STATE_MASK >> View.MEASURED_HEIGHT_STATE_SHIFT));\n"
                + "                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:55: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                + "                | ((child.getMeasuredHeight() >> View.MEASURED_HEIGHT_STATE_SHIFT) & (View.MEASURED_STATE_MASK >> View.MEASURED_HEIGHT_STATE_SHIFT));\n"
                + "                                                                                      ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:90: Warning: Field requires API level 8 (current min is 1): android.R.id#custom [InlinedApi]\n"
                + "        int custom = android.R.id.custom; // API 8\n"
                + "                     ~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:94: Warning: Field requires API level 13 (current min is 1): android.Manifest.permission#SET_POINTER_SPEED [InlinedApi]\n"
                + "        String setPointerSpeed = permission.SET_POINTER_SPEED;\n"
                + "                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:95: Warning: Field requires API level 13 (current min is 1): android.Manifest.permission#SET_POINTER_SPEED [InlinedApi]\n"
                + "        String setPointerSpeed2 = Manifest.permission.SET_POINTER_SPEED;\n"
                + "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:120: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                + "        int y = View.MEASURED_STATE_MASK; // Not OK\n"
                + "                ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:121: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                + "        testBenignUsages(View.MEASURED_STATE_MASK); // Not OK\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck.java:51: Error: Field requires API level 14 (current min is 1): android.widget.ZoomButton#ROTATION_X [NewApi]\n"
                + "        Object rotationX = ZoomButton.ROTATION_X; // Requires API 14\n"
                + "                                      ~~~~~~~~~~\n"
                + "1 errors, 17 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk1.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "apicheck/ApiSourceCheck.java.txt=>src/test/pkg/ApiSourceCheck.java",
                        "apicheck/ApiSourceCheck.class.data=>bin/classes/test/pkg/ApiSourceCheck.class"
                ));
    }

    public void testStyleDeclaration() throws Exception {
        assertEquals(""
                + "res/values/styles2.xml:5: Error: android:actionBarStyle requires API level 11 (current min is 10) [NewApi]\n"
                + "        <item name=\"android:actionBarStyle\">...</item>\n"
                + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk10.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "res/values/styles2.xml"
                ));
    }

    public void testStyleDeclarationInV9() throws Exception {
        assertEquals(""
                + "res/values-v9/styles2.xml:5: Error: android:actionBarStyle requires API level 11 (current min is 10) [NewApi]\n"
                + "        <item name=\"android:actionBarStyle\">...</item>\n"
                + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk10.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "res/values/styles2.xml=>res/values-v9/styles2.xml"
                ));
    }

    public void testStyleDeclarationInV11() throws Exception {
        assertEquals(
                "No warnings.",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk10.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "res/values/styles2.xml=>res/values-v11/styles2.xml"
                ));
    }

    public void testStyleDeclarationInV14() throws Exception {
        assertEquals(
                "No warnings.",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk10.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "res/values/styles2.xml=>res/values-v14/styles2.xml"
                ));
    }

    public void testMovedConstants() throws Exception {
        assertEquals(""
                // These two constants were introduced in API 11; the other 3 were available
                // on subclass ListView from API 1
                + "src/test/pkg/ApiSourceCheck2.java:10: Warning: Field requires API level 11 (current min is 1): android.widget.AbsListView#CHOICE_MODE_MULTIPLE_MODAL [InlinedApi]\n"
                + "        int mode2 = AbsListView.CHOICE_MODE_MULTIPLE_MODAL;\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck2.java:14: Warning: Field requires API level 11 (current min is 1): android.widget.ListView#CHOICE_MODE_MULTIPLE_MODAL [InlinedApi]\n"
                + "        int mode6 = ListView.CHOICE_MODE_MULTIPLE_MODAL;\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk1.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "apicheck/ApiSourceCheck2.java.txt=>src/test/pkg/ApiSourceCheck2.java",
                        "apicheck/ApiSourceCheck2.class.data=>bin/classes/test/pkg/ApiSourceCheck2.class"
                ));
    }

    public void testInheritCompatLibrary() throws Exception {
        assertEquals(""
                + "src/test/pkg/MyActivityImpl.java:8: Error: Call requires API level 11 (current min is 1): android.app.Activity#isChangingConfigurations [NewApi]\n"
                + "  boolean isChanging = super.isChangingConfigurations();\n"
                + "                             ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MyActivityImpl.java:13: Error: Call requires API level 11 (current min is 1): android.app.Activity#isChangingConfigurations [NewApi]\n"
                + "  return super.isChangingConfigurations();\n"
                + "               ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MyActivityImpl.java:12: Error: This method is not overriding anything with the current build target, but will in API level 11 (current target is 3): test.pkg.MyActivityImpl#isChangingConfigurations [Override]\n"
                + " public boolean isChangingConfigurations() {\n"
                + "                ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "3 errors, 0 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk1.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "apicheck/MyActivityImpl.java.txt=>src/test/pkg/MyActivityImpl.java",
                        "apicheck/MyActivityImpl.class.data=>bin/classes/test/pkg/MyActivityImpl.class",
                        "apicheck/android-support-v4.jar.data=>libs/android-support-v4.jar"
                ));
    }

    public void testImplements() throws Exception {
        assertEquals(""
                + "src/test/pkg/ApiCallTest13.java:8: Error: Class requires API level 14 (current min is 4): android.widget.GridLayout [NewApi]\n"
                + "public class ApiCallTest13 extends GridLayout implements\n"
                + "                                   ~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest13.java:9: Error: Class requires API level 11 (current min is 4): android.view.View.OnLayoutChangeListener [NewApi]\n"
                + "  View.OnSystemUiVisibilityChangeListener, OnLayoutChangeListener {\n"
                + "                                           ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest13.java:9: Error: Class requires API level 11 (current min is 4): android.view.View.OnSystemUiVisibilityChangeListener [NewApi]\n"
                + "  View.OnSystemUiVisibilityChangeListener, OnLayoutChangeListener {\n"
                + "       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest13.java:12: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                + "  super(context);\n"
                + "  ~~~~~\n"
                + "4 errors, 0 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "apicheck/ApiCallTest13.java.txt=>src/test/pkg/ApiCallTest13.java",
                        "apicheck/ApiCallTest13.class.data=>bin/classes/test/pkg/ApiCallTest13.class"
                ));
    }

    public void testFieldSuppress() throws Exception {
        // See https://code.google.com/p/android/issues/detail?id=52726
        assertEquals(""
                + "No warnings.",

                lintProject(
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "apicheck/ApiCallTest14.java.txt=>src/test/pkg/ApiCallTest14.java",
                        "apicheck/ApiCallTest14.class.data=>bin/classes/test/pkg/ApiCallTest14.class",
                        "apicheck/ApiCallTest14$1.class.data=>bin/classes/test/pkg/ApiCallTest14$1.class",
                        "apicheck/ApiCallTest14$2.class.data=>bin/classes/test/pkg/ApiCallTest14$2.class",
                        "apicheck/ApiCallTest14$3.class.data=>bin/classes/test/pkg/ApiCallTest14$3.class"
                ));
    }
}
