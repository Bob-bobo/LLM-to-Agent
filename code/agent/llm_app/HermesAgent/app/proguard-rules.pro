# HermesAgent ProGuard/R8 Rules

# Keep LLM provider adapters (reflection via Hilt)
-keep class com.hermes.agent.core.llm.adapter.** { *; }

# Keep Room entities
-keep class com.hermes.agent.core.data.db.entity.** { *; }

# Keep Kotlinx Serialization models
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keep class com.hermes.agent.core.data.model.** { *; }
-keep class com.hermes.agent.core.llm.** { *; }
-keep class com.hermes.agent.core.agent.** { *; }

# Rhino (JS engine)
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }

# OkHttp SSE
-dontwarn okhttp3.internal.sse.**

# Hilt
-dontwarn dagger.hilt.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
