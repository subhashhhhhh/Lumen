package com.subhashh.lumen.data.ai

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.sqrt

/**
 * Can this phone's GPU run a model, and run it right? Asked once per model and system build.
 *
 * The check runs in its own process ([GpuCheckService], `:gpucheck`) because a GPU driver that
 * can't handle the model doesn't throw: LiteRT aborts the whole process (seen on the edge 50 with
 * an earlier export). So the check writes "running" first; if its process dies, that's all the
 * app ever finds, and it uses the CPU. A GPU that does compile must also give the CPU's answer on
 * a fixed test image (fp16 on this model compiled fine and returned wrong vectors).
 */
object GpuCheck {
    private const val Tag = "LumenGpu"
    private const val Timeout = 120_000L

    /** A LiteRT model to check: [id] names its result file; [inputs] floats of test input. */
    class Target(val id: String, val file: File, val inputs: Int)

    private fun target(context: Context, model: AiModel) =
        Target(model.id, File(model.dir(context), model.spec.visualFile), 3 * model.spec.size * model.spec.size)

    private fun file(context: Context, id: String) = File(context.noBackupFilesDir, "gpu-check-$id.txt")

    /** Results only hold for the system build they were taken on (drivers change with updates). */
    private val build get() = Build.FINGERPRINT

    /** True when [model] should run on the GPU. Checks on first call (up to two minutes). */
    suspend fun useGpu(context: Context, model: AiModel): Boolean =
        model.spec.visualFile.endsWith(".tflite") && useGpu(context, target(context, model))

    /** True when [target] should run on the GPU. Checks on first call (up to two minutes). */
    suspend fun useGpu(context: Context, target: Target): Boolean {
        if (!target.file.isFile) return false
        val file = file(context, target.id)
        read(file)?.let { return it }
        file.delete()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val intent = Intent(context, GpuCheckService::class.java)
            .putExtra(GpuCheckService.ExtraId, target.id)
            .putExtra(GpuCheckService.ExtraPath, target.file.path)
            .putExtra(GpuCheckService.ExtraInputs, target.inputs)
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) return false
        try {
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < Timeout) {
                delay(500)
                read(file)?.let { return it }
            }
            // Never answered: the check crashed or hung. Remember, so it isn't retried every run.
            file.writeText("fail\n$build\n")
            Log.w(Tag, "GPU check for ${target.id} didn't finish; using the CPU")
            return false
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }

    /** "ok"/"fail" for this build; null if unknown or left at "running" (crashed: treated as fail). */
    private fun read(file: File): Boolean? {
        val lines = runCatching { file.readLines() }.getOrNull() ?: return null
        if (lines.getOrNull(1) != build) return null
        return when (lines.firstOrNull()) {
            "ok" -> true
            "fail" -> false
            else -> null
        }
    }

    /** Runs in the `:gpucheck` process. */
    internal fun run(context: Context, target: Target) {
        val file = file(context, target.id)
        file.writeText("running\n$build\n")
        val ok = try {
            val path = target.file.path
            val input = FloatArray(target.inputs) { i -> ((i * 7919) % 2000) / 1000f - 1f }
            val gpu = infer(path, Accelerator.GPU, input)
            val cpu = infer(path, Accelerator.CPU, input)
            val agreement = cosine(gpu, cpu)
            Log.i(Tag, "GPU check ${target.id}: agreement $agreement")
            agreement >= 0.99f
        } catch (e: Throwable) {
            Log.w(Tag, "GPU check ${target.id} failed", e)
            false
        }
        file.writeText("${if (ok) "ok" else "fail"}\n$build\n")
    }

    private fun infer(path: String, accelerator: Accelerator, input: FloatArray): FloatArray {
        val environment = Environment.create()
        val options = CompiledModel.Options(accelerator).apply {
            if (accelerator == Accelerator.GPU) {
                gpuOptions = CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP32)
            }
        }
        val model = CompiledModel.create(path, options, environment)
        val inputs = model.createInputBuffers(0)
        val outputs = model.createOutputBuffers(0)
        try {
            inputs[0].writeFloat(input)
            model.run(inputs, outputs, 0)
            // Every output, end to end: the split line reader's second half can be the wrong one.
            val parts = outputs.map { it.readFloat() }
            return FloatArray(parts.sumOf { it.size }).also { all ->
                var at = 0
                for (part in parts) { part.copyInto(all, at); at += part.size }
            }
        } finally {
            inputs.forEach { it.close() }
            outputs.forEach { it.close() }
            model.close()
            environment.close()
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }
}

/** Hosts [GpuCheck.run] in a separate process, so a GPU driver crash can't take Lumen down. */
class GpuCheckService : Service() {
    override fun onBind(intent: Intent): IBinder {
        val target = GpuCheck.Target(
            intent.getStringExtra(ExtraId)!!,
            File(intent.getStringExtra(ExtraPath)!!),
            intent.getIntExtra(ExtraInputs, 0),
        )
        Thread { GpuCheck.run(applicationContext, target) }.start()
        return Binder()
    }

    companion object {
        const val ExtraId = "id"
        const val ExtraPath = "path"
        const val ExtraInputs = "inputs"
    }
}
