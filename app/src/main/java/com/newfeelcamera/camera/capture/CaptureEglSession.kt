package com.newfeelcamera.camera.capture

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import java.io.Closeable

internal class CaptureEglSession private constructor(
    private val display: EGLDisplay,
    private val context: EGLContext,
    private val surface: EGLSurface,
    private val ownerThread: Thread,
) : Closeable {

    private var isClosed = false

    fun makeCurrent() {
        checkOwnerThread()
        check(!isClosed) {
            "CaptureEglSession is already closed."
        }

        checkEgl(
            success = EGL14.eglMakeCurrent(
                display,
                surface,
                surface,
                context,
            ),
            operation = "eglMakeCurrent",
        )
    }

    override fun close() {
        checkOwnerThread()

        if (isClosed) return
        isClosed = true

        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )

        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
        EGL14.eglReleaseThread()
    }

    private fun checkOwnerThread() {
        check(Thread.currentThread() === ownerThread) {
            "Capture EGL must be accessed from ${ownerThread.name}, " +
                "but current thread is ${Thread.currentThread().name}."
        }
    }

    companion object {

        fun create(): CaptureEglSession {
            val ownerThread = Thread.currentThread()

            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)

            check(display != EGL14.EGL_NO_DISPLAY) {
                "eglGetDisplay failed: ${eglErrorMessage()}"
            }

            val versions = IntArray(2)

            checkEgl(
                success = EGL14.eglInitialize(
                    display,
                    versions,
                    0,
                    versions,
                    1,
                ),
                operation = "eglInitialize",
            )

            var context = EGL14.EGL_NO_CONTEXT
            var surface = EGL14.EGL_NO_SURFACE

            try {
                val config = chooseConfig(display)

                context = EGL14.eglCreateContext(
                    display,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(
                        EGL14.EGL_CONTEXT_CLIENT_VERSION,
                        3,
                        EGL14.EGL_NONE,
                    ),
                    0,
                )

                check(context != EGL14.EGL_NO_CONTEXT) {
                    "eglCreateContext failed: ${eglErrorMessage()}"
                }

                surface = EGL14.eglCreatePbufferSurface(
                    display,
                    config,
                    intArrayOf(
                        EGL14.EGL_WIDTH,
                        1,
                        EGL14.EGL_HEIGHT,
                        1,
                        EGL14.EGL_NONE,
                    ),
                    0,
                )

                check(surface != EGL14.EGL_NO_SURFACE) {
                    "eglCreatePbufferSurface failed: ${eglErrorMessage()}"
                }

                val session = CaptureEglSession(
                    display = display,
                    context = context,
                    surface = surface,
                    ownerThread = ownerThread,
                )

                session.makeCurrent()
                return session
            } catch (error: Throwable) {
                if (surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, surface)
                }

                if (context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context)
                }

                EGL14.eglTerminate(display)
                EGL14.eglReleaseThread()

                throw error
            }
        }

        private fun chooseConfig(
            display: EGLDisplay,
        ): EGLConfig {
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)

            checkEgl(
                success = EGL14.eglChooseConfig(
                    display,
                    intArrayOf(
                        EGL14.EGL_RENDERABLE_TYPE,
                        EGLExt.EGL_OPENGL_ES3_BIT_KHR,

                        EGL14.EGL_SURFACE_TYPE,
                        EGL14.EGL_PBUFFER_BIT,

                        EGL14.EGL_RED_SIZE,
                        8,
                        EGL14.EGL_GREEN_SIZE,
                        8,
                        EGL14.EGL_BLUE_SIZE,
                        8,
                        EGL14.EGL_ALPHA_SIZE,
                        8,

                        EGL14.EGL_NONE,
                    ),
                    0,
                    configs,
                    0,
                    configs.size,
                    configCount,
                    0,
                ),
                operation = "eglChooseConfig",
            )

            check(configCount[0] > 0) {
                "No GLES 3 pbuffer EGLConfig was found."
            }

            return checkNotNull(configs[0]) {
                "eglChooseConfig returned a null config."
            }
        }

        private fun checkEgl(
            success: Boolean,
            operation: String,
        ) {
            check(success) {
                "$operation failed: ${eglErrorMessage()}"
            }
        }

        private fun eglErrorMessage(): String =
            "0x${Integer.toHexString(EGL14.eglGetError())}"
    }
}