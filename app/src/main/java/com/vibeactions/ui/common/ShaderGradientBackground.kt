package com.vibeactions.ui.common

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// Nighty Night preset colors from shadergradient (color1=#606080, color2=#8d7dca, color3=#212121)
private val NIGHTY_NIGHT_C1 = floatArrayOf(0.376f, 0.376f, 0.502f)
private val NIGHTY_NIGHT_C2 = floatArrayOf(0.553f, 0.490f, 0.792f)
private val NIGHTY_NIGHT_C3 = floatArrayOf(0.129f, 0.129f, 0.129f)

private class GradientGLRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private var vao = 0
    private var startTime = System.currentTimeMillis()

    private var uTimeLoc = -1
    private var uSpeedLoc = -1
    private var uNoiseDensityLoc = -1
    private var uNoiseStrengthLoc = -1
    private var uColor1Loc = -1
    private var uColor2Loc = -1
    private var uColor3Loc = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.051f, 0.051f, 0.051f, 1.0f)

        val vert = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_SRC)
        val frag = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC)
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vert)
        GLES30.glAttachShader(program, frag)
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vert)
        GLES30.glDeleteShader(frag)

        uTimeLoc = GLES30.glGetUniformLocation(program, "uTime")
        uSpeedLoc = GLES30.glGetUniformLocation(program, "uSpeed")
        uNoiseDensityLoc = GLES30.glGetUniformLocation(program, "uNoiseDensity")
        uNoiseStrengthLoc = GLES30.glGetUniformLocation(program, "uNoiseStrength")
        uColor1Loc = GLES30.glGetUniformLocation(program, "uColor1")
        uColor2Loc = GLES30.glGetUniformLocation(program, "uColor2")
        uColor3Loc = GLES30.glGetUniformLocation(program, "uColor3")

        // Fullscreen quad as triangle strip: BL, BR, TL, TR
        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val vaoArr = IntArray(1)
        val vboArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(1, vboArr, 0)
        vao = vaoArr[0]

        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboArr[0])
        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES30.GL_STATIC_DRAW)

        val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)

        startTime = System.currentTimeMillis()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        GLES30.glUniform1f(uTimeLoc, elapsed)
        GLES30.glUniform1f(uSpeedLoc, 0.25f)
        GLES30.glUniform1f(uNoiseDensityLoc, 1.5f)
        GLES30.glUniform1f(uNoiseStrengthLoc, 0.35f)
        GLES30.glUniform3fv(uColor1Loc, 1, NIGHTY_NIGHT_C1, 0)
        GLES30.glUniform3fv(uColor2Loc, 1, NIGHTY_NIGHT_C2, 0)
        GLES30.glUniform3fv(uColor3Loc, 1, NIGHTY_NIGHT_C3, 0)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    private fun compileShader(type: Int, src: String): Int =
        GLES30.glCreateShader(type).also {
            GLES30.glShaderSource(it, src)
            GLES30.glCompileShader(it)
        }
}

private class GradientGLSurfaceView(context: Context) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setRenderer(GradientGLRenderer())
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

@Composable
fun ShaderGradientBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val glView = remember(context) { GradientGLSurfaceView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(factory = { glView }, modifier = modifier)
}
