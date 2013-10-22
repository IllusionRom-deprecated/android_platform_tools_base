<?xml version="1.0"?>
<globals>
    <global id="topOut" value="." />
    <global id="projectOut" value="." />
    <global id="appCompat" value="${(minApiLevel lt 14)?string('1','')}" />
    <global id="manifestOut" value="${manifestDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="mavenUrl" value="mavenCentral" />
    <global id="buildToolsVersion" value="18.0.1" />
    <global id="gradlePluginVersion" value="0.6.+" />
    <global id="v4SupportLibraryVersion" value="13.0.+" />
</globals>
