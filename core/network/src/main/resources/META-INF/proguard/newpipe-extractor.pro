# Applied automatically by R8 to every app that depends on core:network (NewPipeExtractor).

# YouTube's player JavaScript is run with Rhino, which loads and calls classes reflectively.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
# Optional JavaBeans / javax.script integrations of Rhino that Android does not have.
-dontwarn java.beans.**
-dontwarn javax.script.**

# Relative-date patterns are looked up by class name.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }

# protobuf-javalite messages (InnerTube requests) access their fields reflectively.
-keep class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
