/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package com.microsoft.applicationinsights.agent.internal;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import org.checkerframework.checker.nullness.qual.Nullable;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.bytebuddy.jar.asm.Opcodes.ASM7;

// this is needed because gradle shadow doesn't shade the constant
// "META-INF/services/org.apache.commons.logging.LogFactory" that is in commons-logging code
class CommonsLogFactoryClassFileTransformer implements ClassFileTransformer {

    private static final Logger logger = LoggerFactory.getLogger(CommonsLogFactoryClassFileTransformer.class);

    // using constant here so that it will get shaded appropriately
    // IMPORTANT FOR THIS NOT TO BE FINAL, OTHERWISE COMPILER MAY INLINE IT, WHICH WOULD PREVENT IT FROM BEING SHADED
    public static String SERVICE_ID = "org.apache.commons.logging.LogFactory";

    @Override
    public byte /*@Nullable*/[] transform(@Nullable ClassLoader loader, @Nullable String className,
                                          @Nullable Class<?> classBeingRedefined,
                                          @Nullable ProtectionDomain protectionDomain,
                                          byte[] classfileBuffer) {

        if (!"org/apache/commons/logging/LogFactory".equals(className)) {
            return null;
        }
        if (!className.startsWith("com/microsoft/applicationinsights/")) {
            // only apply if shaded
            return null;
        }
        try {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new CommonsLogFactoryClassVisitor(cw);
            ClassReader cr = new ClassReader(classfileBuffer);
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            return null;
        }
    }

    private static class CommonsLogFactoryClassVisitor extends ClassVisitor {

        private final ClassWriter cw;

        private CommonsLogFactoryClassVisitor(ClassWriter cw) {
            super(ASM7, cw);
            this.cw = cw;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, @Nullable String signature,
                                         String /*@Nullable*/[] exceptions) {
            MethodVisitor mv = cw.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals("getFactory") && descriptor.startsWith("()")) {
                return new CommonsLogFactoryMethodVisitor(mv);
            } else {
                return mv;
            }
        }
    }

    private static class CommonsLogFactoryMethodVisitor extends MethodVisitor {

        private CommonsLogFactoryMethodVisitor(MethodVisitor mv) {
            super(ASM7, mv);
        }

        public void visitLdcInsn(Object value) {
            if ("META-INF/services/org.apache.commons.logging.LogFactory".equals(value)) {
                super.visitLdcInsn("META-INF/services/" + SERVICE_ID);
            } else {
                super.visitLdcInsn(value);
            }
        }
    }
}
