# Keep line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# sherpa-onnx (offline voice): the native library reads these classes and fields by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }
