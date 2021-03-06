package com.example.ecg_androidplot

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.androidplot.ui.HorizontalPositioning
import com.androidplot.ui.LayoutManager
import com.androidplot.ui.VerticalPositioning
import com.androidplot.util.Redrawer
import com.androidplot.xy.*
import java.lang.ref.WeakReference
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import kotlinx.android.synthetic.main.activity_main.*
import java.time.Duration
import java.time.Instant
import java.time.temporal.Temporal
import kotlin.math.roundToInt


/**
 * A simple XYPlot
 */
class MainActivity : Activity() {

    private var plot: XYPlot? = null
    private var redrawer: Redrawer? = Redrawer(plot,30f,false)
    private var data_size = 1800
    private var mediaPlayer: MediaPlayer? = null
    var beep_flag :Boolean = false

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaPlayer = MediaPlayer.create(this,R.raw.ecg_single_beep)
        //mediaPlayer?.isLooping=true
        mediaPlayer?.start()

        plot = findViewById(R.id.ecg_plot)
        val content = object {}.javaClass.getResource("/res/raw/pattern4.txt")!!.readText()
        Toast.makeText(applicationContext, "File loaded", Toast.LENGTH_SHORT).show()
        val content2 = content.split("\t").map { it.toFloat() } // ERROR
        //val series1 = SimpleXYSeries( content2, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY,"Series1")
        val  series1Format = LineAndPointFormatter(Color.RED, null, null, null)
        series1Format.interpolationParams =
            CatmullRomInterpolator.Params(2, CatmullRomInterpolator.Type.Centripetal)
        //plot!!.addSeries(series1, series1Format)

        val ecgSeries = ECGModel(data_size, 200, content2)

        series1Format.isLegendIconEnabled = false

        plot!!.addSeries(ecgSeries, series1Format)
        plot!!.setRangeBoundaries(-0.6, 1.8, BoundaryMode.FIXED) // Y axis boundaries
        plot!!.setDomainBoundaries(0, data_size, BoundaryMode.FIXED) // X axis boundaries
        plot!!.graph.position(0f,HorizontalPositioning.RELATIVE_TO_LEFT,0f,VerticalPositioning.RELATIVE_TO_TOP)

        plot!!.rangeTitle.isVisible = true
        plot!!.legend.isVisible = false
        plot!!.linesPerRangeLabel = 1  // reduce the number of range labels

        // start generating ecg data in the background:
        ecgSeries.start(WeakReference(plot!!.getRenderer(AdvancedLineAndPointRenderer::class.java)))
        redrawer = Redrawer(plot, 60f, true) //Android plot object to force a plot redrawn every x seconds
    }

    inner class ECGModel internal constructor(size: Int, updateFreqHz: Int,values: List<Float> ) : XYSeries {

        private val data: Array<Number> = Array(size) { 0 }
        private val delayMs: Long
        private val thread: Thread
        private var keepRunning = true
        private var latestIndex = 0
        private var rendererRef: WeakReference<AdvancedLineAndPointRenderer?>? = null
        private var upper_beep_threshold :Float = 1.0f
        private var lower_beep_threshold :Float = 0.0f

        private var timeElapsed = 0.0
        private var start : Temporal? = Instant.now()
        private var finish : Temporal? = null

        init {
            var y = 0

            for (i in data.indices) { data[i] = 0 }
            // translate hz into delay (ms):
            delayMs = 1000 / updateFreqHz.toLong()

            thread = Thread(Runnable {
                try {

                    while (keepRunning) {
                        if (latestIndex >= data.size) {
                            latestIndex = 0
                        }
                        data[latestIndex] = values[y]

                        if(values[y] >= upper_beep_threshold && !beep_flag){
                            finish = Instant.now()
                            timeElapsed = ((Duration.between(start, finish).toMillis().toDouble())/1000)*60
                            val bpmString =  """${timeElapsed.roundToInt()} BPM"""
                            bpm_textView.text = bpmString
                            start = Instant.now()
                            beep_flag = true

                            mediaPlayer?.start()
                        }
                        if(values[y] <= lower_beep_threshold && beep_flag) {
                            beep_flag = false
                        }

                        if(y == values.size-1){
                            y=0
                        }
                        else {y++}

                        if (latestIndex < data.size - 1) { // null out the point immediately following i, to disable
                            // connecting i and i+1 with a line:
                            data[latestIndex + 1] = 0
                        }
                        if (rendererRef!!.get() != null) { //ERROR ?
                            rendererRef!!.get()!!.setLatestIndex(latestIndex)
                            Thread.sleep(delayMs)
                        } else {
                            keepRunning = true
                        }
                        latestIndex++
                        Thread.sleep(delayMs)

                    }
                } catch (e: InterruptedException) {
                    keepRunning = false
                }
            })
        }

        fun start(rendererRef: WeakReference<AdvancedLineAndPointRenderer?>?) {
            this.rendererRef = rendererRef
            keepRunning = true
            thread.start()
        }

        override fun size(): Int {
            return data.size
        }

        override fun getX(index: Int): Number {
            return index
        }

        override fun getY(index: Int): Number {
            return data[index]
        }

        override fun getTitle(): String {
            return "Signal"
        }
    }

    public override fun onStop() {
        super.onStop()
        redrawer!!.finish()
        mediaPlayer?.release()//
    }
}