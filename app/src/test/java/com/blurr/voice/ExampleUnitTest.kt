package com.blurr.voice

import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun printMessageClassMethods() {
        val clazz = Class.forName("com.google.ai.edge.litertlm.Message")
        println("=== Message Class Properties/Methods ===")
        clazz.declaredFields.forEach { field ->
            println("Field: ${field.name} (${field.type.name})")
        }
        clazz.methods.forEach { method ->
            println("Method: ${method.name} returns ${method.returnType.name}")
        }
        println("========================================")

        val contentsClazz = Class.forName("com.google.ai.edge.litertlm.Contents")
        println("=== Contents Class Properties/Methods ===")
        contentsClazz.declaredFields.forEach { field ->
            println("Field: ${field.name} (${field.type.name})")
        }
        contentsClazz.methods.forEach { method ->
            println("Method: ${method.name} returns ${method.returnType.name}")
        }
        val getContentsMethod = contentsClazz.getMethod("getContents")
        println("getContents generic return type: ${getContentsMethod.genericReturnType}")
        println("========================================")

        // Let's discover all classes in the package via the JAR file of Message class
        try {
            val location = clazz.protectionDomain.codeSource.location
            println("JAR location: $location")

            try {
                val textContentClazz = Class.forName("com.google.ai.edge.litertlm.Content\$Text")
                println("=== Content\$Text Class Properties/Methods ===")
                textContentClazz.declaredFields.forEach { field ->
                    println("Field: ${field.name} (${field.type.name})")
                }
                textContentClazz.methods.forEach { method ->
                    println("Method: ${method.name} returns ${method.returnType.name}")
                }
                println("========================================")
            } catch (e: Exception) {
                println("Could not load Content\$Text: ${e.message}")
            }

            if (location != null && location.protocol == "file") {
                val file = java.io.File(location.toURI())
                if (file.exists() && file.isFile) {
                    val jar = java.util.jar.JarFile(file)
                    val entries = jar.entries()
                    println("=== ALL CLASSES IN com.google.ai.edge.litertlm package ===")
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (name.startsWith("com/google/ai/edge/litertlm/") && name.endsWith(".class")) {
                            val className = name.replace("/", ".").removeSuffix(".class")
                            println("Class: $className")
                            try {
                                val foundClass = Class.forName(className, false, clazz.classLoader)
                                // Only print if not an anonymous or synthetic class
                                if (!foundClass.isSynthetic && !className.contains("$")) {
                                    println("  - Methods:")
                                    foundClass.methods.forEach { m ->
                                        if (m.declaringClass == foundClass) {
                                            println("    ${m.name}(${m.parameterTypes.map { it.simpleName }.joinToString()}) -> ${m.returnType.simpleName}")
                                        }
                                    }
                                }
                            } catch (e: Throwable) {
                                println("    Could not load/inspect class $className: ${e.message}")
                            }
                        }
                    }
                    println("==========================================================")
                }
            }
        } catch (e: Exception) {
            println("Error listing package classes: ${e.message}")
            e.printStackTrace()
        }
    }
}
