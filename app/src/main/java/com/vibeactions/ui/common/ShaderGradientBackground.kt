package com.vibeactions.ui.common

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Renders the animated gradient via an OpenGL ES 3.0 fragment shader on a fullscreen quad. */
internal class GradientRenderer : GLSurfaceView.Renderer {
    /** Updated from the UI thread when the preset changes; read on the GL thread each frame. */
    @Volatile var colors: FloatArray = GradientPreset.NightyNight.shaderColors

    private var program = 0
    private var positionHandle = 0
    private var uTime = 0
    private var uResolution = 0
    private var uColor1 = 0
    private var uColor2 = 0
    private var uColor3 = 0
    private var width = 1f
    private var height = 1f
    private val startNanos = System.nanoTime()

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = linkProgram(vertex, fragment)
        if (program != 0) {
            positionHandle = GLES30.glGetAttribLocation(program, "a_position")
            uTime = GLES30.glGetUniformLocation(program, "u_time")
            uResolution = GLES30.glGetUniformLocation(program, "u_resolution")
            uColor1 = GLES30.glGetUniformLocation(program, "u_color1")
            uColor2 = GLES30.glGetUniformLocation(program, "u_color2")
            uColor3 = GLES30.glGetUniformLocation(program, "u_color3")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w.toFloat()
        height = h.toFloat()
        GLES30.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES30.glUseProgram(program)
        GLES30.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f)
        GLES30.glUniform2f(uResolution, width * 0.5f, height * 0.5f)
        val c = colors
        GLES30.glUniform3f(uColor1, c[0], c[1], c[2])
        GLES30.glUniform3f(uColor2, c[3], c[4], c[5])
        GLES30.glUniform3f(uColor3, c[6], c[7], c[8])
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(positionHandle)
    }
}

/**
 * Living gradient background backed by a GLSurfaceView running the [FRAGMENT_SHADER]. The GL thread
 * is paused/resumed with the host lifecycle so it doesn't burn the GPU in the background.
 */
@Composable
fun ShaderGradientBackground(preset: GradientPreset, modifier: Modifier = Modifier) {
    val renderer = remember { GradientRenderer() }
    renderer.colors = preset.shaderColors

    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, glView) {
        val view = glView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glView = this
            }
        },
        update = { renderer.colors = preset.shaderColors }
    )
}
